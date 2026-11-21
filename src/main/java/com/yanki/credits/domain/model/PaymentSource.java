package com.yanki.credits.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumera las fuentes posibles de un pago de crédito.
 * Valores posibles: propia cuenta, tercero o efectivo.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Schema(description = "Fuente del pago del crédito")
public enum PaymentSource {
    @Schema(description = "Pago realizado desde la propia cuenta del titular del crédito")
    OWN_ACCOUNT,
    @Schema(description = "Pago realizado por un tercero diferente al titular del crédito")
    THIRD_PARTY,
    @Schema(description = "Pago en efectivo realizado en sucursal o punto de cobranza")
    CASH
}
