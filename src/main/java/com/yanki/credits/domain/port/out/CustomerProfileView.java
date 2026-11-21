package com.yanki.credits.domain.port.out;

import java.time.LocalDateTime;

/**
 * Vista local del perfil del cliente en credits-service.
 * Se actualiza mediante eventos Kafka consumidos desde customers-service.
 *
 * @param customerId identificador del cliente
 * @param customerType tipo de cliente (PERSONAL o ENTERPRISE)
 * @param profile perfil del cliente
 * @param status estado del cliente (ACTIVE, INACTIVE, DELETED)
 * @param updatedAt última actualización
 */
public record CustomerProfileView(
        String customerId,
        String customerType,
        String profile,
        String status,
        LocalDateTime updatedAt
) {
}
