package com.yanki.credits.domain.event;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de cliente registrado (proveniente de customers-service).
 *
 * <p>Topic: {@code yanki.customer.registered.v1}</p>
 *
 * <p>Se consume para crear la vista local del perfil del cliente
 * y permitir la apertura de cr&eacute;ditos sin consultar customers-service por REST.</p>
 *
 * @author Sebasti&aacute;n Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRegisteredEvent {

    /** Identificador del cliente. */
    private String customerId;

    /** Username (email en min&uacute;sculas). */
    private String username;

    /** Email del cliente. */
    private String email;

    /** Contrase&ntilde;a temporal. */
    private String temporaryPassword;

    /** Roles asignados. */
    private List<String> roles;

    /** Tipo de cliente: PERSONAL, ENTERPRISE. */
    private String customerType;

    /** Tipo de documento. */
    private String documentType;

    /** N&uacute;mero de documento. */
    private String documentNumber;

    /** Fecha del evento. */
    private LocalDateTime occurredAt;
}
