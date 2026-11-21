package com.yanki.credits.domain.port.in;

import com.yanki.credits.domain.model.Credit;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;

/**
 * Puerto de entrada para actualizar un crédito existente.
 * Define el caso de uso de reestructuración de crédito (cambio de tasa de interés)
 * sin depender de infraestructura.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface UpdateCreditUseCase {

    /**
     * Actualiza la tasa de interés de un crédito y regenera el cronograma de pagos.
     * Solo se puede actualizar un crédito en estado APPROVED o ACTIVE.
     *
     * @param creditId identificador del crédito a actualizar
     * @param newInterestRate nueva tasa de interés anual
     * @return crédito actualizado con nuevo cronograma
     */
    Single<Credit> execute(String creditId, BigDecimal newInterestRate);
}
