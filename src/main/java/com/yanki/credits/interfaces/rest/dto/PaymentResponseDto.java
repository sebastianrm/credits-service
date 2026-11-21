package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Respuesta para una operacion de pago en un credito.
 * Muestra la distribucion del pago entre capital, interes y comisiones.
 */
@Schema(description = "Respuesta con el detalle de un pago registrado en un credito")
public record PaymentResponseDto(
        @Schema(description = "Identificador unico del pago", example = "pay-xyz789")
        String id,

        @Schema(description = "Identificador del credito al que se aplico el pago", example = "cred-abc123")
        String creditId,

        @Schema(description = "Identificador del cliente titular del credito", example = "cust-12345")
        String customerId,

        @Schema(description = "Monto total del pago en USD", example = "500.00")
        BigDecimal amount,

        @Schema(description = "Porcion del monto aplicada a capital en USD", example = "400.00")
        BigDecimal capitalPortion,

        @Schema(description = "Porcion del monto aplicada a interes en USD", example = "80.00")
        BigDecimal interestPortion,

        @Schema(description = "Porcion del monto aplicada a comisiones y mora en USD", example = "20.00")
        BigDecimal feePortion,

        @Schema(description = "Numero de cuota al que se aplico el pago", example = "3")
        Integer installmentNumber,

        @Schema(description = "Fecha de vencimiento de la cuota (formato YYYY-MM-DD)", example = "2026-12-15")
        LocalDate dueDate,

        @Schema(description = "Fecha y hora en que se realizo el pago (formato ISO-8601)", example = "2026-11-14T14:30:00")
        LocalDateTime paidAt,

        @Schema(description = "Fuente del pago. Valores: OWN_ACCOUNT, THIRD_PARTY, CASH", example = "OWN_ACCOUNT")
        String source,

        @Schema(description = "Identificador de la cuenta de origen del pago", example = "acc-67890")
        String sourceAccountId,

        @Schema(description = "Estado del pago", example = "COMPLETED")
        String status
) {
}
