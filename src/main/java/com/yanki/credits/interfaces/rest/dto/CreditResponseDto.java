package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta con los detalles de un credito.
 * Incluye campos de cronograma, fechas y proposito.
 */
@Schema(description = "Respuesta detallada con la informacion completa de un credito")
public record CreditResponseDto(
        @Schema(description = "Identificador unico del credito", example = "cred-abc123")
        String id,

        @Schema(description = "Identificador del cliente titular del credito", example = "cust-12345")
        String customerId,

        @Schema(description = "Tipo de credito. Valores: PERSONAL, BUSINESS, MORTGAGE", example = "PERSONAL")
        String type,

        @Schema(description = "Estado actual del credito. Valores: PENDING, APPROVED, ACTIVE, PAID_OFF, OVERDUE, CANCELLED", example = "ACTIVE")
        String status,

        @Schema(description = "Monto principal del credito en USD", example = "10000.00")
        BigDecimal principalAmount,

        @Schema(description = "Tasa de interes anual en porcentaje", example = "12.50")
        BigDecimal interestRate,

        @Schema(description = "Plazo total del credito en meses", example = "12")
        Integer termMonths,

        @Schema(description = "Saldo pendiente actual del credito en USD", example = "7500.00")
        BigDecimal outstandingBalance,

        @Schema(description = "Fecha de vencimiento de la proxima cuota (formato YYYY-MM-DD)", example = "2026-12-15")
        LocalDate nextDueDate,

        @Schema(description = "Fecha de la primera cuota (formato YYYY-MM-DD)", example = "2026-11-15")
        LocalDate firstDueDate,

        @Schema(description = "Fecha de vencimiento final del credito (formato YYYY-MM-DD)", example = "2027-10-15")
        LocalDate maturityDate,

        @Schema(description = "Frecuencia de pago del credito", example = "MONTHLY")
        String paymentFrequency,

        @Schema(description = "Proposito del credito (aplicable a creditos empresariales)", example = "Compra de equipo")
        String purpose,

        @Schema(description = "Cronograma de pagos con el detalle de cada cuota")
        List<ScheduleInstallmentResponseDto> schedule,

        @Schema(description = "Fecha y hora de aprobacion del credito (formato ISO-8601)", example = "2026-11-14T10:30:00")
        LocalDateTime approvedAt,

        @Schema(description = "Fecha y hora de creacion del credito (formato ISO-8601)", example = "2026-11-14T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Fecha y hora de ultima actualizacion del credito (formato ISO-8601)", example = "2026-11-14T15:00:00")
        LocalDateTime updatedAt
) {
}
