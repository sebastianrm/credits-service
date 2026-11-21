package com.yanki.credits.domain.port.out;

import io.reactivex.rxjava3.core.Maybe;

/**
 * Puerto de salida para consultar la vista local de riesgo del cliente.
 * Esta vista indica si el cliente tiene deuda vencida y bloquea nuevos productos.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface CustomerRiskPort {

    /**
     * Busca la información de riesgo de un cliente por su identificador.
     *
     * @param customerId identificador del cliente
     * @return información de riesgo o vacío si no existe
     */
    Maybe<CustomerRiskView> findByCustomerId(String customerId);
}
