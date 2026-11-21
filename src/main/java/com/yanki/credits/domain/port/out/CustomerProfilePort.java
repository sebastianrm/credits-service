package com.yanki.credits.domain.port.out;

import io.reactivex.rxjava3.core.Maybe;

/**
 * Puerto de salida para consultar la vista local del perfil del cliente.
 * Esta vista se actualiza mediante eventos Kafka consumidos desde customers-service.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface CustomerProfilePort {

    /**
     * Busca el perfil de un cliente por su identificador.
     *
     * @param customerId identificador del cliente
     * @return perfil del cliente o vacío si no existe
     */
    Maybe<CustomerProfileView> findByCustomerId(String customerId);
}
