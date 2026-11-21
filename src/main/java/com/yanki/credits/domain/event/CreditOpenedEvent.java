package com.yanki.credits.domain.event;

import java.time.LocalDateTime;

/**
 * Payload inmutable de evento de crédito abierto.
 * Se publica cuando un nuevo crédito es creado exitosamente.
 *
 * @param id identificador del evento
 * @param creditId identificador del crédito
 * @param customerId identificador del cliente
 * @param type tipo de crédito
 * @param principalAmount monto principal
 * @param occurredAt fecha y hora del evento
 */
public record CreditOpenedEvent(
        String id,
        String creditId,
        String customerId,
        String type,
        java.math.BigDecimal principalAmount,
        LocalDateTime occurredAt
) implements CreditEvent {
}
