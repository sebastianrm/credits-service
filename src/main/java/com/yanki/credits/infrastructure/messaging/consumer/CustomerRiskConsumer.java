package com.yanki.credits.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanki.credits.infrastructure.persistence.document.CustomerRiskViewDocument;
import com.yanki.credits.infrastructure.persistence.repository.SpringDataCustomerRiskRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka que actualiza la vista local de riesgo del cliente.
 * Escucha eventos de mora de credits-service propio y de otros servicios
 * para mantener la proyección de riesgo sincronizada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerRiskConsumer {

    private final SpringDataCustomerRiskRepository repository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "yanki.credit.overdue.v1", groupId = "credits-service")
    public void onCreditOverdue(String message) {
        log.info("[Kafka] Consumiendo evento credit.overdue.v1");
        try {
            JsonNode node = objectMapper.readTree(message);
            String customerId = node.get("customerId").asText();

            log.warn("[Kafka] Actualizando vista de riesgo (hasOverdueDebt=true): customerId={}", customerId);

            CustomerRiskViewDocument doc = CustomerRiskViewDocument.builder()
                    .id(customerId)
                    .customerId(customerId)
                    .hasOverdueDebt(true)
                    .updatedAt(LocalDateTime.now())
                    .build();

            repository.save(doc).subscribe(
                    saved -> log.info("[Kafka] Vista de riesgo actualizada exitosamente: customerId={}", customerId),
                    e -> log.error("[Kafka] Error actualizando vista de riesgo: customerId={}, error={}",
                            customerId, e.getMessage())
            );
        } catch (Exception e) {
            log.error("[Kafka] Error procesando evento de mora: {}", e.getMessage(), e);
        }
    }
}
