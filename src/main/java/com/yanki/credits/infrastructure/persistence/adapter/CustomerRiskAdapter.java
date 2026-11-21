package com.yanki.credits.infrastructure.persistence.adapter;

import com.yanki.credits.domain.port.out.CustomerRiskPort;
import com.yanki.credits.domain.port.out.CustomerRiskView;
import com.yanki.credits.infrastructure.persistence.repository.SpringDataCustomerRiskRepository;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Adaptador de salida que implementa la consulta de la vista local de riesgo del cliente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRiskAdapter implements CustomerRiskPort {

    private final SpringDataCustomerRiskRepository repository;

    @Override
    public Maybe<CustomerRiskView> findByCustomerId(String customerId) {
        log.debug("[MongoDB] Buscando vista de riesgo: customerId={}", customerId);
        return RxJava3Adapter.monoToMaybe(
                repository.findByCustomerId(customerId)
                        .map(doc -> new CustomerRiskView(
                                doc.getCustomerId(),
                                doc.isHasOverdueDebt(),
                                doc.getUpdatedAt()
                        ))
        ).doOnSuccess(view -> log.debug("[MongoDB] Riesgo encontrado: customerId={}, hasOverdueDebt={}",
                customerId, view.hasOverdueDebt()))
                .doOnError(e -> log.error("[MongoDB] Error buscando riesgo: customerId={}, error={}",
                        customerId, e.getMessage()));
    }
}
