package com.yanki.credits.domain.port.in;

import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.model.PaymentSource;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de entrada para registrar pagos de créditos.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface RegisterPaymentUseCase {

    /**
     * Registra un pago en el crédito indicado.
     *
     * @param creditId identificador del crédito
     * @param amount monto del pago
     * @param source fuente del pago
     * @param sourceAccountId cuenta origen si aplica
     * @param payerCustomerId cliente pagador si es de terceros
     * @return pago registrado
     */
    Single<Payment> execute(String creditId, java.math.BigDecimal amount,
            PaymentSource source, String sourceAccountId, String payerCustomerId);

    /**
     * Lista los pagos de un crédito.
     *
     * @param creditId identificador del crédito
     * @return flujo de pagos
     */
    Flowable<Payment> listByCredit(String creditId);

    /**
     * Busca un pago por su identificador dentro de un crédito.
     *
     * @param creditId identificador del crédito
     * @param paymentId identificador del pago
     * @return pago encontrado o vacío
     */
    Maybe<Payment> findById(String creditId, String paymentId);
}
