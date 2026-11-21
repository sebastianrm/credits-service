package com.yanki.credits.domain.port.out;

import java.time.LocalDateTime;

/**
 * Vista local de riesgo del cliente en credits-service.
 * Indica si el cliente tiene deuda vencida que bloquea nuevos productos.
 *
 * @param customerId identificador del cliente
 * @param hasOverdueDebt indica si el cliente tiene deuda vencida mayor a 30 días
 * @param updatedAt última actualización
 */
public record CustomerRiskView(
        String customerId,
        boolean hasOverdueDebt,
        LocalDateTime updatedAt
) {
}
