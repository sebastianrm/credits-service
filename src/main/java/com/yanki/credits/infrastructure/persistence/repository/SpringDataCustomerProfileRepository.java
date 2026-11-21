package com.yanki.credits.infrastructure.persistence.repository;

import com.yanki.credits.infrastructure.persistence.document.CustomerProfileViewDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio Spring Data para la vista local del perfil del cliente.
 */
@Repository
public interface SpringDataCustomerProfileRepository extends ReactiveMongoRepository<CustomerProfileViewDocument, String> {

    /**
     * Busca el perfil de un cliente por su identificador.
     *
     * @param customerId identificador del cliente
     * @return perfil encontrado o vacío
     */
    Mono<CustomerProfileViewDocument> findByCustomerId(String customerId);
}
