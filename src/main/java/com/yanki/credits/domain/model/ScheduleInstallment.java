package com.yanki.credits.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Representa una cuota individual dentro del cronograma de pagos de un crédito.
 * Contiene detalles de número de cuota, fecha de vencimiento, importes y estado.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class ScheduleInstallment {

    private final int installmentNumber;
    private final LocalDate dueDate;
    private final BigDecimal capitalAmount;
    private final BigDecimal interestAmount;
    private final BigDecimal feeAmount;
    private final BigDecimal totalAmount;
    private final String status;

    /**
     * Crea una nueva cuota del cronograma.
     *
     * @param installmentNumber número de cuota
     * @param dueDate fecha de vencimiento
     * @param capitalAmount monto de capital
     * @param interestAmount monto de interés
     * @param feeAmount monto de comisión
     * @return nueva cuota con el total calculado
     */
    public static ScheduleInstallment create(int installmentNumber, LocalDate dueDate,
            BigDecimal capitalAmount, BigDecimal interestAmount, BigDecimal feeAmount) {
        BigDecimal total = capitalAmount.add(interestAmount).add(feeAmount)
                .setScale(2, RoundingMode.HALF_UP);
        return ScheduleInstallment.builder()
                .installmentNumber(installmentNumber)
                .dueDate(dueDate)
                .capitalAmount(capitalAmount.setScale(2, RoundingMode.HALF_UP))
                .interestAmount(interestAmount.setScale(2, RoundingMode.HALF_UP))
                .feeAmount(feeAmount.setScale(2, RoundingMode.HALF_UP))
                .totalAmount(total)
                .status("PENDING")
                .build();
    }
}
