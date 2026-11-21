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
 * Documento MongoDB para la colección de vistas locales de riesgo del cliente.
 * Indica si el cliente tiene deuda vencida que bloquea nuevos productos.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customer_risk_view")
public class CustomerRiskViewDocument {

    @Id
    private String id;

    @Indexed
    private String customerId;

    private boolean hasOverdueDebt;
    private LocalDateTime updatedAt;
}
