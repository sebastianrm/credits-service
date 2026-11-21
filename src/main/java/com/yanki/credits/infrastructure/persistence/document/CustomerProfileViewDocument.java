package com.yanki.credits.infrastructure.persistence.document;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB para la colección de vistas locales del perfil del cliente.
 * Se actualiza mediante eventos Kafka consumidos desde customers-service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customer_profile_view")
public class CustomerProfileViewDocument {

    @Id
    private String id;

    @Indexed
    private String customerId;

    private String customerType;
    private String profile;
    private String status;
    private LocalDateTime updatedAt;
}
