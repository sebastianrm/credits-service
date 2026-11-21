package com.yanki.credits.domain.port.out;

import com.yanki.credits.domain.model.Credit;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de salida para la persistencia de créditos.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface CreditPersistencePort {

    /**
     * Busca un crédito por su identificador.
     *
     * @param id identificador del crédito
     * @return crédito encontrado o vacío
     */
    Maybe<Credit> findById(String id);

    /**
     * Guarda o actualiza un crédito.
     *
     * @param credit entidad de dominio a persistir
     * @return crédito persistido
     */
    Single<Credit> save(Credit credit);

    /**
     * Lista los créditos de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo de créditos
     */
    Flowable<Credit> findByCustomerId(String customerId);

    /**
     * Cuenta los créditos activos de un cliente por tipo.
     *
     * @param customerId identificador del cliente
     * @param type tipo de crédito
     * @return cantidad de créditos
     */
    Single<Long> countByCustomerIdAndType(String customerId, String type);

    /**
     * Lista todos los créditos.
     *
     * @return flujo de todos los créditos
     */
    Flowable<Credit> findAll();

    /**
     * Lista los créditos activos con cuotas vencidas mayor a 30 días.
     * Se usa en el job diario de detección de mora.
     *
     * @return flujo de créditos en mora
     */
    Flowable<Credit> findOverdueCredits();

    /**
     * Cuenta los créditos activos de un cliente.
     *
     * @param customerId identificador del cliente
     * @return cantidad de créditos activos
     */
    Single<Long> countActiveByCustomerId(String customerId);
}
