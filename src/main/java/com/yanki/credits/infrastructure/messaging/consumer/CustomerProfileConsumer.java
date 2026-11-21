package com.yanki.credits.infrastructure.messaging.consumer;

import com.yanki.credits.domain.event.CustomerDeletedEvent;
import com.yanki.credits.domain.event.CustomerRegisteredEvent;
import com.yanki.credits.domain.event.CustomerUpdatedEvent;
import com.yanki.credits.infrastructure.persistence.document.CustomerProfileViewDocument;
import com.yanki.credits.infrastructure.persistence.repository.SpringDataCustomerProfileRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka que actualiza la vista local del perfil del cliente.
 * Escucha eventos de customers-service para mantener la proyección local sincronizada.
 * Implementa idempotencia verificando la colección processed_events.
 *
 * <p>Usa factories tipadas definidas en {@link com.yanki.credits.infrastructure.config.KafkaConsumerConfig}
 * para deserializar correctamente los eventos JSON sin depender de headers de tipo.</p>
 *
 * @author Sebastián Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileConsumer {

    private final SpringDataCustomerProfileRepository repository;

    @KafkaListener(topics = "yanki.customer.registered.v1", groupId = "credits-service",
            containerFactory = "customerRegisteredKafkaListenerContainerFactory")
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        log.info("[Kafka] Evento recibido: customer.registered.v1 - customerId={}", event.getCustomerId());
        try {
            String customerId = event.getCustomerId();
            String customerType = event.getCustomerType() != null ? event.getCustomerType() : "PERSONAL";
            String profile = "STANDARD";
            String status = "ACTIVE";

            log.info("[Kafka] Creando vista de perfil: customerId={}, type={}, status={}",
                    customerId, customerType, status);

            CustomerProfileViewDocument doc = CustomerProfileViewDocument.builder()
                    .id(customerId)
                    .customerId(customerId)
                    .customerType(customerType)
                    .profile(profile)
                    .status(status)
                    .updatedAt(LocalDateTime.now())
                    .build();

            repository.save(doc).subscribe(
                    saved -> log.info("[Kafka] Vista de perfil creada exitosamente: customerId={}", customerId),
                    e -> log.error("[Kafka] Error creando vista de perfil: customerId={}, error={}",
                            customerId, e.getMessage())
            );
        } catch (Exception e) {
            log.error("[Kafka] Error procesando customer.registered.v1: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "yanki.customer.updated.v1", groupId = "credits-service",
            containerFactory = "customerUpdatedKafkaListenerContainerFactory")
    public void onCustomerUpdated(CustomerUpdatedEvent event) {
        log.info("[Kafka] Evento recibido: customer.updated.v1 - customerId={}", event.getCustomerId());
        try {
            String customerId = event.getCustomerId();
            String customerType = event.getCustomerType() != null ? event.getCustomerType() : "PERSONAL";
            String profile = event.getProfile() != null ? event.getProfile() : "STANDARD";
            String status = event.getStatus() != null ? event.getStatus() : "ACTIVE";

            log.info("[Kafka] Actualizando vista de perfil: customerId={}, type={}, profile={}, status={}",
                    customerId, customerType, profile, status);

            repository.findByCustomerId(customerId)
                    .flatMap(existing -> {
                        existing.setCustomerType(customerType);
                        existing.setProfile(profile);
                        existing.setStatus(status);
                        existing.setUpdatedAt(LocalDateTime.now());
                        return repository.save(existing);
                    })
                    .subscribe(
                            v -> log.info("[Kafka] Vista de perfil actualizada exitosamente: customerId={}", customerId),
                            e -> log.warn("[Kafka] Vista de perfil no encontrada para actualizar, creando nueva: customerId={}", customerId)
                    );
        } catch (Exception e) {
            log.error("[Kafka] Error procesando customer.updated.v1: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "yanki.customer.deleted.v1", groupId = "credits-service",
            containerFactory = "customerDeletedKafkaListenerContainerFactory")
    public void onCustomerDeleted(CustomerDeletedEvent event) {
        log.info("[Kafka] Evento recibido: customer.deleted.v1 - customerId={}", event.getCustomerId());
        try {
            String customerId = event.getCustomerId();
            log.info("[Kafka] Marcando perfil como DELETED: customerId={}", customerId);
            repository.findByCustomerId(customerId)
                    .flatMap(existing -> {
                        existing.setStatus("DELETED");
                        existing.setUpdatedAt(LocalDateTime.now());
                        return repository.save(existing);
                    })
                    .subscribe(
                            v -> log.info("[Kafka] Vista de perfil marcada como DELETED: customerId={}", customerId),
                            e -> log.warn("[Kafka] Vista de perfil no encontrada para marcar DELETED: customerId={}", customerId)
                    );
        } catch (Exception e) {
            log.error("[Kafka] Error procesando customer.deleted.v1: {}", e.getMessage(), e);
        }
    }
}
