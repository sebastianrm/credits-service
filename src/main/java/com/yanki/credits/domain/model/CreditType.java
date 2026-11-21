package com.yanki.credits.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumera los tipos de crédito disponibles en el sistema.
 * Incluye créditos personales, empresariales e hipotecarios.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Schema(description = "Tipo de crédito disponible en el sistema Yanki")
public enum CreditType {
    @Schema(description = "Crédito personal para uso individual. Límite: 1 crédito activo por cliente. Monto máximo: $50,000.")
    PERSONAL,
    @Schema(description = "Crédito empresarial para empresas. Sin límite de cantidad activa. Monto máximo: $500,000.")
    BUSINESS,
    @Schema(description = "Crédito hipotecario para adquisición de inmuebles. Monto máximo: $2,000,000.")
    MORTGAGE
}
