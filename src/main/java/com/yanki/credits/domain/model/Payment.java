package com.yanki.credits.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Entidad pura de dominio que representa un pago registrado en un crédito.
 * Contiene información sobre monto, desglose, fuente y estado del pago.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Payment {

    private final String id;
    private final String creditId;
    private final String customerId;
    private final BigDecimal amount;
    private final BigDecimal capitalPortion;
    private final BigDecimal interestPortion;
    private final BigDecimal feePortion;
    private final int installmentNumber;
    private final LocalDate dueDate;
    private final LocalDateTime paidAt;
    private final PaymentSource source;
    private final String sourceAccountId;
    private final String payerCustomerId;
    private final String status;
    private final LocalDateTime createdAt;

    /**
     * Crea un nuevo pago completado.
     *
     * @param creditId identificador del crédito
     * @param customerId identificador del cliente titular
     * @param amount monto del pago
     * @param source fuente del pago
     * @param sourceAccountId cuenta origen si aplica
     * @param payerCustomerId cliente pagador si es de terceros
     * @return nueva instancia de pago
     */
    public static Payment create(String creditId, String customerId, BigDecimal amount,
            PaymentSource source, String sourceAccountId, String payerCustomerId) {
        return Payment.builder()
                .id(UUID.randomUUID().toString())
                .creditId(creditId)
                .customerId(customerId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .capitalPortion(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .interestPortion(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .feePortion(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .source(source != null ? source : PaymentSource.OWN_ACCOUNT)
                .sourceAccountId(sourceAccountId)
                .payerCustomerId(payerCustomerId)
                .status("COMPLETED")
                .paidAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Corrige el monto de un pago ya registrado.
     * Se utiliza cuando se detecta un error en el monto ingresado.
     *
     * @param newAmount nuevo monto del pago
     * @return nueva instancia del pago con el monto corregido
     */
    public Payment correct(BigDecimal newAmount) {
        return this.toBuilder()
                .amount(newAmount.setScale(2, RoundingMode.HALF_UP))
                .status("CORRECTED")
                .build();
    }

    /**
     * Revierte un pago, marcándolo como anulado.
     * El monto se devuelve al saldo pendiente del crédito.
     *
     * @return nueva instancia del pago con estado REVERSED
     */
    public Payment reverse() {
        return this.toBuilder()
                .status("REVERSED")
                .build();
    }
}
