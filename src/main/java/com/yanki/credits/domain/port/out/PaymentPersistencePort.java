package com.yanki.credits.domain.port.out;

import com.yanki.credits.domain.model.Payment;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de salida para la persistencia de pagos.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface PaymentPersistencePort {

    /**
     * Busca un pago por su identificador.
     *
     * @param id identificador del pago
     * @return pago encontrado o vacío
     */
    Maybe<Payment> findById(String id);

    /**
     * Guarda un pago.
     *
     * @param payment entidad de dominio a persistir
     * @return pago persistido
     */
    Single<Payment> save(Payment payment);

    /**
     * Lista los pagos de un crédito.
     *
     * @param creditId identificador del crédito
     * @return flujo de pagos
     */
    Flowable<Payment> findByCreditId(String creditId);
}
