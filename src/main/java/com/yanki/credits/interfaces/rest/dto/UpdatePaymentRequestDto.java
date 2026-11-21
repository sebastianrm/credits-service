package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO para la solicitud de corrección de un pago registrado.
 */
public record UpdatePaymentRequestDto(
        @Schema(description = "Nuevo monto del pago. Debe ser mayor a cero.", example = "1500.00", required = true)
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        BigDecimal amount
) {
}
