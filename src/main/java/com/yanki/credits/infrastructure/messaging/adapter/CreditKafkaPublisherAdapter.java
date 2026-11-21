package com.yanki.credits.infrastructure.messaging.adapter;

import com.yanki.credits.domain.event.CreditEvent;
import com.yanki.credits.domain.event.CreditOpenedEvent;
import com.yanki.credits.domain.event.CreditOverdueEvent;
import com.yanki.credits.domain.event.CreditPaymentRegisteredEvent;
import com.yanki.credits.domain.port.out.CreditEventPublisherPort;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Mono;

/**
 * Adaptador de salida que publica eventos de creditos hacia Apache Kafka.
 * Soporta eventos de apertura, pago y mora de créditos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditKafkaPublisherAdapter implements CreditEventPublisherPort {

    private static final String TOPIC_CREDIT_OPENED = "yanki.credit.opened.v1";
    private static final String TOPIC_PAYMENT_REGISTERED = "yanki.credit.payment.registered.v1";
    private static final String TOPIC_CREDIT_OVERDUE = "yanki.credit.overdue.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Completable publish(CreditEvent event) {
        String topic;
        String key;

        if (event instanceof CreditOpenedEvent opened) {
            topic = TOPIC_CREDIT_OPENED;
            key = opened.creditId();
            log.info("[Kafka] Publicando evento {}: creditId={}", topic, key);
            log.debug("[Kafka] Clase del evento publicado: {}", event.getClass().getName());
            log.debug("[Kafka] CreditOpenedEvent campos -> id={}, creditId={}, customerId={}, type={}, principalAmount={}, occurredAt={}",
                    opened.id(), opened.creditId(), opened.customerId(), opened.type(), opened.principalAmount(), opened.occurredAt());
        } else if (event instanceof CreditPaymentRegisteredEvent payment) {
            topic = TOPIC_PAYMENT_REGISTERED;
            key = payment.creditId();
            log.info("[Kafka] Publicando evento {}: creditId={}", topic, key);
            log.debug("[Kafka] Clase del evento publicado: {}", event.getClass().getName());
            log.debug("[Kafka] CreditPaymentRegisteredEvent campos -> id={}, creditId={}, paymentId={}, customerId={}, amount={}, occurredAt={}",
                    payment.id(), payment.creditId(), payment.paymentId(), payment.customerId(), payment.amount(), payment.occurredAt());
        } else if (event instanceof CreditOverdueEvent overdue) {
            topic = TOPIC_CREDIT_OVERDUE;
            key = overdue.customerId();
            log.info("[Kafka] Publicando evento {}: customerId={}", topic, key);
            log.debug("[Kafka] Clase del evento publicado: {}", event.getClass().getName());
            log.debug("[Kafka] CreditOverdueEvent campos -> id={}, creditId={}, customerId={}, daysOverdue={}, outstandingAmount={}, occurredAt={}",
                    overdue.id(), overdue.creditId(), overdue.customerId(), overdue.daysOverdue(), overdue.outstandingAmount(), overdue.occurredAt());
        } else {
            return Completable.error(new IllegalArgumentException("Tipo de evento desconocido"));
        }

        Mono<Void> sendMono = Mono.fromFuture(() -> kafkaTemplate.send(topic, key, event))
                .doOnNext(result -> log.info("[Kafka] Evento publicado en {} con clave {}", topic, key))
                .then();
        return RxJava3Adapter.monoToCompletable(sendMono);
    }
}
