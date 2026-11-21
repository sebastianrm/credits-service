package com.yanki.credits.infrastructure.persistence.adapter;

import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.port.out.PaymentPersistencePort;
import com.yanki.credits.infrastructure.persistence.mapper.PaymentDocumentMapper;
import com.yanki.credits.infrastructure.persistence.repository.SpringDataPaymentRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Adaptador de salida que implementa la persistencia de pagos sobre MongoDB Reactivo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMongoAdapter implements PaymentPersistencePort {

    private final SpringDataPaymentRepository repository;

    @Override
    public Maybe<Payment> findById(String id) {
        log.debug("[MongoDB] Buscando pago por ID: {}", id);
        return RxJava3Adapter.monoToMaybe(
                repository.findById(id).map(PaymentDocumentMapper::toDomain)
        ).doOnSuccess(p -> log.debug("[MongoDB] Pago encontrado: id={}", id))
                .doOnError(e -> log.error("[MongoDB] Error buscando pago: id={}, error={}", id, e.getMessage()));
    }

    @Override
    public Single<Payment> save(Payment payment) {
        log.debug("[MongoDB] Guardando pago: id={}, creditId={}", payment.getId(), payment.getCreditId());
        return RxJava3Adapter.monoToSingle(
                repository.save(PaymentDocumentMapper.toDocument(payment))
                        .map(PaymentDocumentMapper::toDomain)
        ).doOnSuccess(saved -> log.debug("[MongoDB] Pago guardado: id={}", saved.getId()))
                .doOnError(e -> log.error("[MongoDB] Error guardando pago: id={}, error={}", payment.getId(), e.getMessage()));
    }

    @Override
    public Flowable<Payment> findByCreditId(String creditId) {
        log.debug("[MongoDB] Buscando pagos por creditId: {}", creditId);
        return RxJava3Adapter.fluxToFlowable(
                repository.findByCreditId(creditId).map(PaymentDocumentMapper::toDomain)
        ).doOnError(e -> log.error("[MongoDB] Error buscando pagos por creditId: {}, error={}", creditId, e.getMessage()));
    }
}
