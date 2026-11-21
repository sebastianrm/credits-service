package com.yanki.credits.domain.port.out;

import com.yanki.credits.domain.event.CreditEvent;
import io.reactivex.rxjava3.core.Completable;

/**
 * Puerto de salida para publicar eventos de créditos hacia Kafka.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface CreditEventPublisherPort {

    /**
     * Publica un evento de crédito.
     *
     * @param event payload inmutable del evento
     * @return completable cuando la publicacion finaliza
     */
    Completable publish(CreditEvent event);
}
