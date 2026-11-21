package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.port.in.UpdatePaymentUseCase;
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
 * Caso de uso encargado de corregir un pago registrado.
 * Permite modificar el monto de un pago cuando se detecta un error,
 * ajustando el saldo del crédito según la diferencia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePaymentService implements UpdatePaymentUseCase {

    private final CreditPersistencePort creditPersistencePort;
    private final PaymentPersistencePort paymentPersistencePort;
    private final CachePort cachePort;

    @Override
    public Single<Payment> execute(String creditId, String paymentId, BigDecimal newAmount) {
        log.info("Iniciando corrección de pago: creditId={}, paymentId={}, newAmount={}",
                creditId, paymentId, newAmount);

        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Corrección rechazada - monto inválido: paymentId={}, newAmount={}", paymentId, newAmount);
            return Single.error(new BusinessException(
                    "INVALID_PAYMENT_AMOUNT", "El monto corregido debe ser mayor a cero"));
        }

        return creditPersistencePort.findById(creditId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Crédito no encontrado para corregir pago: {}", creditId);
                    return new BusinessException(
                            "CREDIT_NOT_FOUND", "Crédito no encontrado: " + creditId);
                }))
                .flatMap(credit -> {
                    log.debug("Crédito encontrado para corregir pago: creditId={}, status={}",
                            creditId, credit.getStatus());
                    if (credit.getStatus() == CreditStatus.CANCELLED) {
                        log.warn("Corrección rechazada - crédito cancelado: creditId={}", creditId);
                        return Single.<Payment>error(new BusinessException(
                                "FORBIDDEN_OPERATION", "No se puede corregir un pago de un crédito cancelado"));
                    }
                    if (credit.getStatus() == CreditStatus.PAID_OFF) {
                        log.warn("Corrección rechazada - crédito pagado: creditId={}", creditId);
                        return Single.<Payment>error(new BusinessException(
                                "CREDIT_ALREADY_PAID", "No se puede corregir un pago de un crédito pagado totalmente"));
                    }

                    return paymentPersistencePort.findById(paymentId)
                            .switchIfEmpty(Single.error(() -> {
                                log.warn("Pago no encontrado para corregir: paymentId={}", paymentId);
                                return new BusinessException(
                                        "PAYMENT_NOT_FOUND", "Pago no encontrado: " + paymentId);
                            }))
                            .flatMap(payment -> {
                                if (!payment.getCreditId().equals(creditId)) {
                                    log.warn("Corrección rechazada - pago no pertenece al crédito: paymentId={}, creditId={}",
                                            paymentId, creditId);
                                    return Single.<Payment>error(new BusinessException(
                                            "PAYMENT_NOT_IN_CREDIT", "El pago no pertenece al crédito indicado"));
                                }
                                if ("REVERSED".equals(payment.getStatus())) {
                                    log.warn("Corrección rechazada - pago revertido: paymentId={}", paymentId);
                                    return Single.<Payment>error(new BusinessException(
                                            "PAYMENT_REVERSED", "No se puede corregir un pago revertido"));
                                }

                                BigDecimal originalAmount = payment.getAmount();
                                log.info("Corrigiendo pago: paymentId={}, originalAmount={}, newAmount={}, difference={}",
                                        paymentId, originalAmount, newAmount, newAmount.subtract(originalAmount));

                                // Corregir el pago
                                Payment correctedPayment = payment.correct(newAmount);

                                // Ajustar el saldo del crédito por la diferencia
                                Credit adjustedCredit = credit.adjustPaymentCorrection(originalAmount, newAmount);
                                log.info("Saldo del crédito ajustado: creditId={}, newBalance={}",
                                        creditId, adjustedCredit.getOutstandingBalance());

                                return creditPersistencePort.save(adjustedCredit)
                                        .doOnSuccess(saved -> log.debug("Crédito ajustado persistido: creditId={}", creditId))
                                        .flatMap(savedCredit ->
                                                paymentPersistencePort.save(correctedPayment)
                                                        .doOnSuccess(saved -> log.info("Pago corregido persistido: paymentId={}", saved.getId()))
                                                        .flatMap(saved -> {
                                                            // Invalidar caché del cronograma
                                                            Completable sideEffect = invalidateCache(creditId);
                                                            return sideEffect.andThen(Single.just(saved));
                                                        }));
                            });
                })
                .doOnSuccess(p -> log.info("✅ Pago corregido exitosamente: paymentId={}, creditId={}, newAmount={}",
                        p.getId(), creditId, newAmount))
                .doOnError(e -> log.error("Error corrigiendo pago {} en crédito {}: {}",
                        paymentId, creditId, e.getMessage()));
    }

    /**
     * Invalida la caché del cronograma de un crédito después de una corrección.
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
