package com.yanki.credits.infrastructure.persistence.adapter;

import com.yanki.credits.domain.port.out.CustomerProfilePort;
import com.yanki.credits.domain.port.out.CustomerProfileView;
import com.yanki.credits.infrastructure.persistence.document.CustomerProfileViewDocument;
import com.yanki.credits.infrastructure.persistence.repository.SpringDataCustomerProfileRepository;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Adaptador de salida que implementa la consulta de la vista local del perfil del cliente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileAdapter implements CustomerProfilePort {

    private final SpringDataCustomerProfileRepository repository;

    @Override
    public Maybe<CustomerProfileView> findByCustomerId(String customerId) {
        log.debug("[MongoDB] Buscando perfil del cliente: customerId={}", customerId);
        return RxJava3Adapter.monoToMaybe(
                repository.findByCustomerId(customerId)
                        .map(doc -> new CustomerProfileView(
                                doc.getCustomerId(),
                                doc.getCustomerType(),
                                doc.getProfile(),
                                doc.getStatus(),
                                doc.getUpdatedAt()
                        ))
        ).doOnSuccess(view -> log.debug("[MongoDB] Perfil encontrado: customerId={}, type={}, status={}",
                customerId, view.customerType(), view.status()))
                .doOnError(e -> log.error("[MongoDB] Error buscando perfil: customerId={}, error={}",
                        customerId, e.getMessage()));
    }
}
