package com.yanki.credits.domain.port.in;

import com.yanki.credits.domain.model.Payment;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;

/**
 * Puerto de entrada para corregir un pago registrado.
 * Permite modificar el monto de un pago ya registrado, por ejemplo
 * cuando se detecta un error en el monto ingresado.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface UpdatePaymentUseCase {

    /**
     * Corrige el monto de un pago registrado.
     *
     * @param creditId identificador del crédito
     * @param paymentId identificador del pago a corregir
     * @param newAmount nuevo monto del pago
     * @return pago corregido
     */
    Single<Payment> execute(String creditId, String paymentId, BigDecimal newAmount);
}
