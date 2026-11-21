package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.port.in.ReversePaymentUseCase;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.yanki.credits.domain.port.out.PaymentPersistencePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caso de uso encargado de revertir un pago registrado.
 * Al revertir un pago, se devuelve el monto al saldo pendiente del crédito
 * y se marca el pago con estado REVERSED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReversePaymentService implements ReversePaymentUseCase {

    private final CreditPersistencePort creditPersistencePort;
    private final PaymentPersistencePort paymentPersistencePort;
    private final CachePort cachePort;

    @Override
    public Single<Payment> execute(String creditId, String paymentId) {
        log.info("Iniciando reversa de pago: creditId={}, paymentId={}", creditId, paymentId);

        return creditPersistencePort.findById(creditId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Crédito no encontrado para revertir pago: {}", creditId);
                    return new BusinessException(
                            "CREDIT_NOT_FOUND", "Crédito no encontrado: " + creditId);
                }))
                .flatMap(credit -> {
                    log.debug("Crédito encontrado para revertir pago: creditId={}, status={}, balance={}",
                            creditId, credit.getStatus(), credit.getOutstandingBalance());
                    if (credit.getStatus() == CreditStatus.CANCELLED) {
                        log.warn("Reversa rechazada - crédito cancelado: creditId={}", creditId);
                        return Single.<Payment>error(new BusinessException(
                                "FORBIDDEN_OPERATION", "No se puede revertir un pago de un crédito cancelado"));
                    }

                    return paymentPersistencePort.findById(paymentId)
                            .switchIfEmpty(Single.error(() -> {
                                log.warn("Pago no encontrado para revertir: paymentId={}", paymentId);
                                return new BusinessException(
                                        "PAYMENT_NOT_FOUND", "Pago no encontrado: " + paymentId);
                            }))
                            .flatMap(payment -> {
                                if (!payment.getCreditId().equals(creditId)) {
                                    log.warn("Reversa rechazada - pago no pertenece al crédito: paymentId={}, creditId={}",
                                            paymentId, creditId);
                                    return Single.<Payment>error(new BusinessException(
                                            "PAYMENT_NOT_IN_CREDIT", "El pago no pertenece al crédito indicado"));
                                }
                                if ("REVERSED".equals(payment.getStatus())) {
                                    log.warn("Reversa rechazada - pago ya revertido: paymentId={}", paymentId);
                                    return Single.<Payment>error(new BusinessException(
                                            "PAYMENT_ALREADY_REVERSED", "El pago ya fue revertido"));
                                }

                                BigDecimal paymentAmount = payment.getAmount();
                                log.info("Revertiendo pago: paymentId={}, amount={}", paymentId, paymentAmount);

                                // Revertir el pago
                                Payment reversedPayment = payment.reverse();

                                // Devolver el monto al saldo del crédito
                                Credit revertedCredit = credit.reversePayment(paymentAmount);
                                log.info("Saldo del crédito restaurado: creditId={}, newBalance={}",
                                        creditId, revertedCredit.getOutstandingBalance());

                                return creditPersistencePort.save(revertedCredit)
                                        .doOnSuccess(saved -> log.debug("Crédito restaurado persistido: creditId={}", creditId))
                                        .flatMap(savedCredit ->
                                                paymentPersistencePort.save(reversedPayment)
                                                        .doOnSuccess(saved -> log.info("Pago revertido persistido: paymentId={}", saved.getId()))
                                                        .flatMap(saved -> {
                                                            // Invalidar caché del cronograma
                                                            Completable sideEffect = invalidateCache(creditId);
                                                            return sideEffect.andThen(Single.just(saved));
                                                        }));
                            });
                })
                .doOnSuccess(p -> log.info("✅ Pago revertido exitosamente: paymentId={}, creditId={}, amount={}",
                        p.getId(), creditId, p.getAmount()))
                .doOnError(e -> log.error("Error revirtiendo pago {} en crédito {}: {}",
                        paymentId, creditId, e.getMessage()));
    }

    /**
     * Invalida la caché del cronograma de un crédito después de una reversa.
     *
     * @param creditId identificador del crédito
     * @return completable cuando la caché se invalida
     */
    private Completable invalidateCache(String creditId) {
        String key = "yanki:credits:schedule:" + creditId;
        return cachePort.delete(key)
                .doOnComplete(() -> log.debug("Caché invalidada para crédito {}", creditId))
                .onErrorComplete();
    }
}
