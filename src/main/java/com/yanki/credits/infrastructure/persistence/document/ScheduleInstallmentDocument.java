package com.yanki.credits.infrastructure.persistence.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Documento embebido para las cuotas del cronograma de pagos.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleInstallmentDocument {

    private Integer installmentNumber;
    private LocalDate dueDate;
    private BigDecimal capitalAmount;
    private BigDecimal interestAmount;
    private BigDecimal feeAmount;
    private BigDecimal totalAmount;
    private String status;
}
