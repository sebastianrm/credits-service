package com.yanki.credits.infrastructure.persistence.adapter;

import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.yanki.credits.infrastructure.persistence.mapper.CreditDocumentMapper;
import com.yanki.credits.infrastructure.persistence.repository.SpringDataCreditRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Adaptador de salida que implementa la persistencia de creditos sobre MongoDB Reactivo.
 * Convierte entre Project Reactor (Mono/Flux) y RxJava 3 (Single/Maybe/Flowable).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditMongoAdapter implements CreditPersistencePort {

    private final SpringDataCreditRepository repository;

    @Override
    public Maybe<Credit> findById(String id) {
        log.debug("[MongoDB] Buscando crédito por ID: {}", id);
        return RxJava3Adapter.monoToMaybe(
                repository.findById(id).map(CreditDocumentMapper::toDomain)
        ).doOnSuccess(c -> log.debug("[MongoDB] Crédito encontrado: id={}", id))
                .doOnError(e -> log.error("[MongoDB] Error buscando crédito: id={}, error={}", id, e.getMessage()));
    }

    @Override
    public Single<Credit> save(Credit credit) {
        log.debug("[MongoDB] Guardando crédito: id={}, customerId={}", credit.getId(), credit.getCustomerId());
        return RxJava3Adapter.monoToSingle(
                repository.save(CreditDocumentMapper.toDocument(credit))
                        .map(CreditDocumentMapper::toDomain)
        ).doOnSuccess(saved -> log.debug("[MongoDB] Crédito guardado: id={}", saved.getId()))
                .doOnError(e -> log.error("[MongoDB] Error guardando crédito: id={}, error={}", credit.getId(), e.getMessage()));
    }

    @Override
    public Flowable<Credit> findByCustomerId(String customerId) {
        log.debug("[MongoDB] Buscando créditos por customerId: {}", customerId);
        return RxJava3Adapter.fluxToFlowable(
                repository.findByCustomerId(customerId).map(CreditDocumentMapper::toDomain)
        ).doOnError(e -> log.error("[MongoDB] Error buscando créditos por customerId: {}, error={}", customerId, e.getMessage()));
    }

    @Override
    public Single<Long> countByCustomerIdAndType(String customerId, String type) {
        log.debug("[MongoDB] Contando créditos: customerId={}, type={}", customerId, type);
        return RxJava3Adapter.monoToSingle(
                repository.countByCustomerIdAndType(customerId, type)
        ).doOnSuccess(count -> log.debug("[MongoDB] Conteo: customerId={}, type={}, count={}", customerId, type, count))
                .doOnError(e -> log.error("[MongoDB] Error contando créditos: customerId={}, error={}", customerId, e.getMessage()));
    }

    @Override
    public Flowable<Credit> findAll() {
        log.debug("[MongoDB] Listando todos los créditos");
        return RxJava3Adapter.fluxToFlowable(
                repository.findAll().map(CreditDocumentMapper::toDomain)
        ).doOnError(e -> log.error("[MongoDB] Error listando créditos: {}", e.getMessage()));
    }

    @Override
    public Flowable<Credit> findOverdueCredits() {
        LocalDate threshold = LocalDate.now().minusDays(30);
        java.util.List<String> statuses = Arrays.asList("ACTIVE", "OVERDUE");
        log.debug("[MongoDB] Buscando créditos en mora: threshold={}, statuses={}", threshold, statuses);
        return RxJava3Adapter.fluxToFlowable(
                repository.findByStatusInAndNextDueDateBefore(statuses, threshold)
                        .map(CreditDocumentMapper::toDomain)
        ).doOnError(e -> log.error("[MongoDB] Error buscando créditos en mora: {}", e.getMessage()));
    }

    @Override
    public Single<Long> countActiveByCustomerId(String customerId) {
        java.util.List<String> activeStatuses = Arrays.asList("ACTIVE", "APPROVED");
        log.debug("[MongoDB] Contando créditos activos: customerId={}", customerId);
        return RxJava3Adapter.monoToSingle(
                repository.countByCustomerIdAndStatusIn(customerId, activeStatuses)
        ).doOnSuccess(count -> log.debug("[MongoDB] Conteo activos: customerId={}, count={}", customerId, count))
                .doOnError(e -> log.error("[MongoDB] Error contando créditos activos: customerId={}, error={}", customerId, e.getMessage()));
    }
}
