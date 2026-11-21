package com.yanki.credits.domain.port.in;

import com.yanki.credits.domain.model.Credit;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;

/**
 * Puerto de entrada para cancelar un crédito existente.
 * Define el caso de uso de cancelación de crédito sin depender de infraestructura.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface CancelCreditUseCase {

    /**
     * Cancela un crédito cambiando su estado a CANCELLED.
     * Solo se puede cancelar un crédito en estado APPROVED o ACTIVE.
     *
     * @param creditId identificador del crédito a cancelar
     * @return crédito cancelado
     */
    Single<Credit> execute(String creditId);
}
