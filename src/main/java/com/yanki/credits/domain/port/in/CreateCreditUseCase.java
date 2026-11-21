package com.yanki.credits.domain.port.in;

import com.yanki.credits.domain.model.Credit;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de entrada para crear un nuevo crédito.
 * Define el caso de uso de apertura de crédito sin depender de infraestructura.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface CreateCreditUseCase {

    /**
     * Crea un nuevo crédito para el cliente indicado.
     *
     * @param customerId identificador del cliente
     * @param type tipo de crédito
     * @param principalAmount monto principal
     * @param interestRate tasa de interés
     * @param termMonths plazo en meses
     * @return crédito creado
     */
    Single<Credit> execute(String customerId, String type,
            java.math.BigDecimal principalAmount, java.math.BigDecimal interestRate, int termMonths);
}
