package com.yanki.credits.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload inmutable de evento de crédito en mora.
 * Se publica cuando el job diario detecta créditos con mora mayor a 30 días.
 *
 * @param id identificador del evento
 * @param creditId identificador del crédito
 * @param customerId identificador del cliente
 * @param daysOverdue días en mora
 * @param outstandingAmount saldo pendiente
 * @param occurredAt fecha y hora del evento
 */
public record CreditOverdueEvent(
        String id,
        String creditId,
        String customerId,
        int daysOverdue,
        BigDecimal outstandingAmount,
        LocalDateTime occurredAt
) implements CreditEvent {
}
