package com.yanki.credits.domain.service;

import com.yanki.credits.domain.event.CreditPaymentRegisteredEvent;
import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.model.PaymentSource;
import com.yanki.credits.domain.port.in.RegisterPaymentUseCase;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditEventPublisherPort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.yanki.credits.domain.port.out.PaymentPersistencePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caso de uso encargado de registrar pagos en créditos.
 * Valida el estado del crédito, aplica el pago con prioridad según reglas de negocio,
 * actualiza el saldo, invalida caché y persiste el pago.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterPaymentService implements RegisterPaymentUseCase {

    private final CreditPersistencePort creditPersistencePort;
    private final PaymentPersistencePort paymentPersistencePort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final CachePort cachePort;

    @Override
    public Single<Payment> execute(String creditId, BigDecimal amount,
            PaymentSource source, String sourceAccountId, String payerCustomerId) {
        log.info("Iniciando registro de pago: creditId={}, amount={}, source={}",
                creditId, amount, source);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Pago rechazado - monto inválido: creditId={}, amount={}", creditId, amount);
            return Single.error(new BusinessException(
                    "INVALID_PAYMENT_AMOUNT", "El monto del pago debe ser mayor a cero"));
        }

        return creditPersistencePort.findById(creditId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Pago rechazado - crédito no encontrado: {}", creditId);
                    return new BusinessException(
                            "CREDIT_NOT_FOUND", "Crédito no encontrado: " + creditId);
                }))
                .flatMap(credit -> {
                    log.debug("Crédito encontrado para pago: creditId={}, status={}, balance={}",
                            creditId, credit.getStatus(), credit.getOutstandingBalance());
                    if (credit.getStatus() == CreditStatus.CANCELLED) {
                        log.warn("Pago rechazado - crédito cancelado: creditId={}", creditId);
                        return Single.<Payment>error(new BusinessException(
                                "FORBIDDEN_OPERATION", "No se puede realizar pago sobre un crédito cancelado"));
                    }
                    if (credit.getStatus() == CreditStatus.PAID_OFF) {
                        log.warn("Pago rechazado - crédito ya pagado: creditId={}", creditId);
                        return Single.<Payment>error(new BusinessException(
                                "CREDIT_ALREADY_PAID", "El crédito ya ha sido pagado totalmente"));
                    }

                    // Aplicar pago al crédito
                    Credit updatedCredit = credit.applyPayment(amount);
                    log.info("Pago aplicado al crédito: creditId={}, amount={}, newBalance={}",
                            creditId, amount, updatedCredit.getOutstandingBalance());

                    return creditPersistencePort.save(updatedCredit)
                            .doOnSuccess(saved -> log.debug("Crédito actualizado en MongoDB: creditId={}", creditId))
                            .flatMap(savedCredit -> {
                                Payment payment = Payment.create(
                                        creditId, savedCredit.getCustomerId(), amount,
                                        source, sourceAccountId, payerCustomerId);
                                log.info("Entidad de pago creada: paymentId={}, creditId={}, amount={}",
                                        payment.getId(), creditId, amount);
                                return paymentPersistencePort.save(payment)
                                        .doOnSuccess(saved -> log.info("Pago persistido en MongoDB: paymentId={}", saved.getId()))
                                        .flatMap(saved -> {
                                            // Invalidar caché del cronograma y publicar evento
                                            Completable sideEffects = invalidateCache(creditId)
                                                    .andThen(publishEvent(saved, creditId));
                                            return sideEffects.andThen(Single.just(saved));
                                        });
                            });
                })
                .doOnSuccess(p -> log.info("Pago registrado exitosamente: paymentId={}, creditId={}, amount={}, source={}",
                        p.getId(), creditId, p.getAmount(), source))
                .doOnError(e -> log.error("Error registrando pago en crédito {}: {}", creditId, e.getMessage()));
    }

    @Override
    public Flowable<Payment> listByCredit(String creditId) {
        log.debug("Listando pagos del crédito: {}", creditId);
        return creditPersistencePort.findById(creditId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Crédito no encontrado para listar pagos: {}", creditId);
                    return new BusinessException(
                            "CREDIT_NOT_FOUND", "Crédito no encontrado");
                }))
                .toFlowable()
                .flatMap(credit -> paymentPersistencePort.findByCreditId(creditId))
                .doOnNext(p -> log.debug("Pago encontrado: paymentId={}, amount={}",
                        p.getId(), p.getAmount()));
    }

    @Override
    public Maybe<Payment> findById(String creditId, String paymentId) {
        log.debug("Buscando pago: creditId={}, paymentId={}", creditId, paymentId);
        return creditPersistencePort.findById(creditId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Crédito no encontrado para buscar pago: {}", creditId);
                    return new BusinessException(
                            "CREDIT_NOT_FOUND", "Crédito no encontrado");
                }))
                .toMaybe()
                .flatMap(c -> paymentPersistencePort.findById(paymentId)
                        .filter(p -> p.getCreditId().equals(creditId))
                        .doOnSuccess(p -> log.debug("Pago encontrado: paymentId={}, amount={}",
                                p.getId(), p.getAmount()))
                        .switchIfEmpty(Maybe.error(() -> {
                            log.warn("Pago no encontrado: creditId={}, paymentId={}", creditId, paymentId);
                            return new BusinessException(
                                    "PAYMENT_NOT_FOUND", "Pago no encontrado");
                        })));
    }

    /**
     * Invalida la caché del cronograma de un crédito después de un pago.
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

    /**
     * Publica el evento de pago registrado en Kafka.
     *
     * @param payment pago registrado
     * @param creditId identificador del crédito
     * @return completable cuando el evento se publica
     */
    private Completable publishEvent(Payment payment, String creditId) {
        CreditPaymentRegisteredEvent event = new CreditPaymentRegisteredEvent(
                UUID.randomUUID().toString(),
                creditId,
                payment.getId(),
                payment.getCustomerId(),
                payment.getAmount(),
                LocalDateTime.now()
        );
        log.info("Publicando evento credit.payment.registered.v1: paymentId={}, creditId={}, amount={}",
                payment.getId(), creditId, payment.getAmount());
        return eventPublisherPort.publish(event)
                .doOnComplete(() -> log.info("Evento credit.payment.registered.v1 publicado: paymentId={}",
                        payment.getId()))
                .doOnError(e -> log.error("Error publicando evento credit.payment.registered.v1: paymentId={}, error={}",
                        payment.getId(), e.getMessage()));
    }
}
