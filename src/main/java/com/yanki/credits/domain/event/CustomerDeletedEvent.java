package com.yanki.credits.domain.event;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de cliente eliminado (proveniente de customers-service).
 *
 * <p>Topic: {@code yanki.customer.deleted.v1}</p>
 *
 * <p>Se consume para marcar la vista local del cliente como DELETED,
 * impidiendo nuevas operaciones de cr&eacute;ditos sobre ese cliente.</p>
 *
 * @author Sebasti&aacute;n Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDeletedEvent {

    /** Identificador del cliente. */
    private String customerId;

    /** Fecha del evento. */
    private LocalDateTime occurredAt;
}
