package com.yanki.credits.domain.port.in;

import com.yanki.credits.domain.model.Payment;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de entrada para revertir un pago registrado.
 * Permite anular un pago ya registrado, devolviendo el monto
 * al saldo pendiente del crédito.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface ReversePaymentUseCase {

    /**
     * Revierte un pago registrado, devolviendo el monto al saldo del crédito.
     *
     * @param creditId identificador del crédito
     * @param paymentId identificador del pago a revertir
     * @return pago revertido con estado REVERSED
     */
    Single<Payment> execute(String creditId, String paymentId);
}
