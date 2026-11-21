package com.yanki.credits.infrastructure.persistence.document;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Documento MongoDB para la coleccion de pagos.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payments")
public class PaymentDocument {

    @Id
    private String id;

    @Indexed
    private String creditId;

    @Indexed
    private String customerId;
    private BigDecimal amount;
    private BigDecimal capitalPortion;
    private BigDecimal interestPortion;
    private BigDecimal feePortion;
    private Integer installmentNumber;
    private LocalDate dueDate;
    private LocalDateTime paidAt;
    private String source;
    private String sourceAccountId;
    private String payerCustomerId;
    private String status;
    private LocalDateTime createdAt;
}
