package com.yanki.credits.domain.event;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de cliente actualizado (proveniente de customers-service).
 *
 * <p>Topic: {@code yanki.customer.updated.v1}</p>
 *
 * <p>Se consume para actualizar la vista local del perfil del cliente
 * cuando cambian el tipo, perfil o estado del cliente.</p>
 *
 * @author Sebasti&aacute;n Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdatedEvent {

    /** Identificador del cliente. */
    private String customerId;

    /** Tipo de cliente: PERSONAL, ENTERPRISE. */
    private String customerType;

    /** Perfil: STANDARD, VIP, PYME. */
    private String profile;

    /** Estado: ACTIVE, BLOCKED, DELETED. */
    private String status;

    /** Fecha del evento. */
    private LocalDateTime occurredAt;
}
