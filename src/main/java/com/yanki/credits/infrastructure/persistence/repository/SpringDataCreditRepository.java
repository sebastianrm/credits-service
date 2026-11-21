package com.yanki.credits.infrastructure.persistence.repository;

import com.yanki.credits.infrastructure.persistence.document.CreditDocument;
import java.time.LocalDate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio Spring Data reactivo para creditos en MongoDB.
 */
public interface SpringDataCreditRepository extends ReactiveMongoRepository<CreditDocument, String> {

    Flux<CreditDocument> findByCustomerId(String customerId);

    Mono<Long> countByCustomerIdAndType(String customerId, String type);

    /**
     * Lista los créditos activos con nextDueDate vencida mayor a 30 días.
     *
     * @param threshold fecha límite (hoy - 30 días)
     * @return flujo de créditos en mora
     */
    Flux<CreditDocument> findByStatusInAndNextDueDateBefore(java.util.List<String> statuses, LocalDate threshold);

    /**
     * Cuenta los créditos activos de un cliente.
     *
     * @param customerId identificador del cliente
     * @return cantidad de créditos activos
     */
    Mono<Long> countByCustomerIdAndStatusIn(String customerId, java.util.List<String> statuses);
}
