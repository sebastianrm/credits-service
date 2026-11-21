package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Respuesta con los detalles de una cuota del cronograma de pagos.
 * El cronograma se genera con el sistema francés (cuotas fijas mensuales).
 */
@Schema(description = "Detalle de una cuota individual del cronograma de pagos del credito")
public record ScheduleInstallmentResponseDto(
        @Schema(description = "Numero secuencial de la cuota", example = "1")
        Integer installmentNumber,

        @Schema(description = "Fecha de vencimiento de la cuota (formato YYYY-MM-DD)", example = "2026-12-15")
        LocalDate dueDate,

        @Schema(description = "Monto de capital de esta cuota en USD", example = "400.00")
        BigDecimal capitalAmount,

        @Schema(description = "Monto de interes de esta cuota en USD", example = "83.33")
        BigDecimal interestAmount,

        @Schema(description = "Monto de comisiones y mora de esta cuota en USD", example = "0.00")
        BigDecimal feeAmount,

        @Schema(description = "Monto total de la cuota (capital + interes + comisiones) en USD", example = "483.33")
        BigDecimal totalAmount,

        @Schema(description = "Estado de la cuota. Valores: PENDING, PAID, OVERDUE", example = "PENDING")
        String status
) {
}
