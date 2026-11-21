package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Solicitud para actualizar la tasa de interés de un crédito existente.
 * Se usa para reestructuraciones de crédito que regeneran el cronograma de pagos.
 */
@Schema(description = "Solicitud para actualizar la tasa de interés de un crédito existente")
public record UpdateCreditRequestDto(
        @Schema(description = "Nueva tasa de interés anual en porcentaje. Rango válido: 0.01 a 50.00",
                example = "10.50", minimum = "0.01", maximum = "50.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal interestRate
) {
}
