package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Solicitud para registrar un pago de credito.
 * El pago se aplica con orden de prioridad: 1) Mora/Comisiones, 2) Interes vencido, 3) Capital vencido,
 * 4) Interes corriente, 5) Capital corriente, 6) Anticipado.
 */
@Schema(description = "Solicitud para registrar un pago en un credito existente")
public record CreatePaymentRequestDto(
        @Schema(description = "Monto del pago en moneda local (USD)", example = "500.00", minimum = "0.01", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,

        @Schema(description = "Fuente del pago. Valores: OWN_ACCOUNT, THIRD_PARTY, CASH", example = "OWN_ACCOUNT", allowableValues = {"OWN_ACCOUNT", "THIRD_PARTY", "CASH"})
        String source,

        @Schema(description = "Identificador de la cuenta de origen del pago (obligatorio si source es OWN_ACCOUNT o THIRD_PARTY)", example = "acc-67890")
        String sourceAccountId,

        @Schema(description = "Identificador del cliente que realiza el pago (obligatorio si source es THIRD_PARTY)", example = "cust-99999")
        String payerCustomerId,

        @Schema(description = "Descripcion opcional del pago", example = "Pago mensual de credito")
        String description
) {
}
