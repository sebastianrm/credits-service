package com.yanki.credits.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumera los posibles estados en el ciclo de vida de un crédito.
 * Incluye estados como pendiente, aprobado, activo, pagado, en mora y cancelado.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Schema(description = "Estado actual del crédito en su ciclo de vida")
public enum CreditStatus {
    @Schema(description = "Crédito creado pero aún no aprobado")
    PENDING,
    @Schema(description = "Crédito aprobado con cronograma generado pero aún no activado")
    APPROVED,
    @Schema(description = "Crédito activo y en período de pagos")
    ACTIVE,
    @Schema(description = "Crédito completamente pagado y cerrado")
    PAID_OFF,
    @Schema(description = "Crédito con mora mayor a 30 días. Bloquea nuevos productos para el cliente.")
    OVERDUE,
    @Schema(description = "Crédito cancelado antes de su vencimiento")
    CANCELLED
}
