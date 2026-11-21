package com.yanki.credits.domain.port.out;

import com.yanki.credits.domain.model.Credit;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de salida para la detección de créditos en mora.
 * Permite consultar créditos con cuotas vencidas mayor a 30 días.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface OverdueDetectionPort {

    /**
     * Lista los créditos con cuotas vencidas mayor a 30 días.
     *
     * @return flujo de créditos en mora
     */
    Flowable<Credit> findOverdueCredits();

    /**
     * Cuenta los días en mora de un crédito.
     *
     * @param credit crédito a evaluar
     * @return días en mora
     */
    Single<Integer> countDaysOverdue(Credit credit);
}
