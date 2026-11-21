package com.yanki.credits.infrastructure.persistence.repository;

import com.yanki.credits.infrastructure.persistence.document.CustomerRiskViewDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio Spring Data para la vista local de riesgo del cliente.
 */
@Repository
public interface SpringDataCustomerRiskRepository extends ReactiveMongoRepository<CustomerRiskViewDocument, String> {

    /**
     * Busca la información de riesgo de un cliente por su identificador.
     *
     * @param customerId identificador del cliente
     * @return información de riesgo encontrada o vacío
     */
    Mono<CustomerRiskViewDocument> findByCustomerId(String customerId);
}
