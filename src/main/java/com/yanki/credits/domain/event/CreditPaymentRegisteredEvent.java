package com.yanki.credits.domain.event;

import java.time.LocalDateTime;

/**
 * Payload inmutable de evento de pago registrado.
 * Se publica cuando un pago es registrado exitosamente en un crédito.
 *
 * @param id identificador del evento
 * @param creditId identificador del crédito
 * @param paymentId identificador del pago
 * @param customerId identificador del cliente
 * @param amount monto del pago
 * @param occurredAt fecha y hora del evento
 */
public record CreditPaymentRegisteredEvent(
        String id,
        String creditId,
        String paymentId,
        String customerId,
        java.math.BigDecimal amount,
        LocalDateTime occurredAt
) implements CreditEvent {
}
