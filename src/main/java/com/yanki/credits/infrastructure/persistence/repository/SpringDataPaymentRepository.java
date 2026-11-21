package com.yanki.credits.infrastructure.persistence.repository;

import com.yanki.credits.infrastructure.persistence.document.PaymentDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio Spring Data reactivo para pagos en MongoDB.
 */
public interface SpringDataPaymentRepository extends ReactiveMongoRepository<PaymentDocument, String> {

    Flux<PaymentDocument> findByCreditId(String creditId);
}
