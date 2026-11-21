package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Solicitud para crear un nuevo credito.
 * Valida reglas de negocio: perfil del cliente, deuda vencida, limite de creditos personales y scoring.
 */
@Schema(description = "Solicitud para crear un nuevo credito en el sistema Yanki")
public record CreateCreditRequestDto(
        @Schema(description = "Identificador unico del cliente solicitante", example = "cust-12345", requiredMode = Schema.RequiredMode.REQUIRED)
        String customerId,

        @Schema(description = "Tipo de credito a solicitar. Valores: PERSONAL, BUSINESS, MORTGAGE", example = "PERSONAL", allowableValues = {"PERSONAL", "BUSINESS", "MORTGAGE"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String type,

        @Schema(description = "Monto principal solicitado en moneda local (USD)", example = "10000.00", minimum = "0.01")
        BigDecimal amount,

        @Schema(description = "Plazo del credito en meses. Rango valido: 1 a 36 meses.", example = "12", minimum = "1", maximum = "36")
        Integer termMonths,

        @Schema(description = "Tasa de interes anual en porcentaje", example = "12.50", minimum = "0.01")
        BigDecimal interestRate,

        @Schema(description = "Proposito del credito (obligatorio para creditos empresariales)", example = "Compra de equipo de oficina")
        String purpose,

        @Schema(description = "Descripcion del garantia ofrecida (aplicable a creditos hipotecarios)", example = "Inmueble ubicado en Av. Principal 123")
        String collateralDescription
) {
}
