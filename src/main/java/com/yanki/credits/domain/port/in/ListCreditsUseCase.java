package com.yanki.credits.domain.port.in;

import com.yanki.credits.domain.model.Credit;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Puerto de entrada para consultar créditos.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface ListCreditsUseCase {

    /**
     * Busca un crédito por su identificador.
     *
     * @param id identificador del crédito
     * @return crédito encontrado o vacío
     */
    Maybe<Credit> findById(String id);

    /**
     * Lista los créditos filtrando por cliente, tipo y estado.
     *
     * @param customerId identificador del cliente (opcional)
     * @param type tipo de crédito (opcional)
     * @param status estado del crédito (opcional)
     * @return flujo de créditos que coinciden
     */
    Flowable<Credit> findByCriteria(String customerId, String type, String status);
}
