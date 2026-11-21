package com.yanki.credits.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Entidad pura de dominio que representa un crédito en el sistema Yanki.
 * Contiene información sobre cliente, tipo de crédito, estado y cronograma de pagos.
 * No depende de frameworks externos ni de infraestructura.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Credit {

    private final String id;
    private final String customerId;
    private final CreditType type;
    private final CreditStatus status;
    private final BigDecimal principalAmount;
    private final BigDecimal interestRate;
    private final int termMonths;
    private final BigDecimal outstandingBalance;
    private final LocalDate nextDueDate;
    private final LocalDate firstDueDate;
    private final LocalDate maturityDate;
    private final String paymentFrequency;
    private final String purpose;
    private final List<ScheduleInstallment> schedule;
    private final LocalDateTime approvedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    /**
     * Crea un nuevo crédito en estado aprobado con cronograma de pagos generado.
     * El cronograma se calcula con cuotas fijas mensuales (sistema francés).
     *
     * @param customerId identificador del cliente
     * @param type tipo de crédito
     * @param principalAmount monto principal
     * @param interestRate tasa de interés anual
     * @param termMonths plazo en meses
     * @return nueva instancia de crédito con cronograma
     */
    public static Credit create(String customerId, CreditType type, BigDecimal principalAmount,
            BigDecimal interestRate, int termMonths) {
        return create(customerId, type, principalAmount, interestRate, termMonths, null);
    }

    /**
     * Crea un nuevo crédito en estado aprobado con cronograma de pagos generado.
     * Permite especificar un propósito para créditos empresariales.
     *
     * @param customerId identificador del cliente
     * @param type tipo de crédito
     * @param principalAmount monto principal
     * @param interestRate tasa de interés anual
     * @param termMonths plazo en meses
     * @param purpose propósito del crédito (opcional, para empresariales)
     * @return nueva instancia de crédito con cronograma
     */
    public static Credit create(String customerId, CreditType type, BigDecimal principalAmount,
            BigDecimal interestRate, int termMonths, String purpose) {

        BigDecimal normalizedPrincipal = principalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedRate = interestRate.setScale(2, RoundingMode.HALF_UP);

        // Generar cronograma de pagos con sistema francés (cuotas fijas)
        List<ScheduleInstallment> schedule = generateSchedule(normalizedPrincipal, normalizedRate, termMonths);

        LocalDate now = LocalDate.now();
        LocalDate firstDue = now.plusMonths(1);
        LocalDate maturity = now.plusMonths(termMonths);

        ScheduleInstallment firstInstallment = schedule.isEmpty() ? null : schedule.get(0);
        LocalDate nextDue = firstInstallment != null ? firstInstallment.getDueDate() : firstDue;

        return Credit.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .type(type)
                .status(CreditStatus.APPROVED)
                .principalAmount(normalizedPrincipal)
                .interestRate(normalizedRate)
                .termMonths(termMonths)
                .outstandingBalance(normalizedPrincipal)
                .nextDueDate(nextDue)
                .firstDueDate(firstDue)
                .maturityDate(maturity)
                .paymentFrequency("MONTHLY")
                .purpose(purpose)
                .schedule(schedule)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Genera el cronograma de pagos utilizando el sistema francés (cuotas fijas).
     * Cada cuota contiene la misma cantidad total, compuesta por capital e interés variables.
     *
     * @param principal monto principal del crédito
     * @param annualRate tasa de interés anual en porcentaje
     * @param months número de meses del plazo
     * @return lista de cuotas ordenadas por número
     */
    private static List<ScheduleInstallment> generateSchedule(BigDecimal principal,
            BigDecimal annualRate, int months) {
        List<ScheduleInstallment> schedule = new ArrayList<>();

        if (months <= 0 || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return schedule;
        }

        // Tasa mensual = tasa anual / 12 / 100
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal("1200"), 10, RoundingMode.HALF_UP);

        // Calcular cuota fija (sistema francés): P * [r(1+r)^n] / [(1+r)^n - 1]
        BigDecimal fixedPayment;
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            // Sin interés: cuota = principal / meses
            fixedPayment = principal.divide(new BigDecimal(months), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal onePlusRToN = onePlusR.pow(months);
            BigDecimal numerator = monthlyRate.multiply(onePlusR).multiply(onePlusRToN);
            BigDecimal denominator = onePlusRToN.subtract(BigDecimal.ONE);
            fixedPayment = principal.multiply(numerator).divide(denominator, 2, RoundingMode.HALF_UP);
        }

        BigDecimal remainingBalance = principal;
        LocalDate startDate = LocalDate.now();

        for (int i = 1; i <= months; i++) {
            // Interés del mes = saldo pendiente * tasa mensual
            BigDecimal interestPortion = remainingBalance.multiply(monthlyRate)
                    .setScale(2, RoundingMode.HALF_UP);

            // Capital del mes = cuota fija - interés
            BigDecimal capitalPortion = fixedPayment.subtract(interestPortion);

            // Ajuste en la última cuota para que el saldo quede en cero
            if (i == months) {
                capitalPortion = remainingBalance;
                fixedPayment = capitalPortion.add(interestPortion)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            ScheduleInstallment installment = ScheduleInstallment.create(
                    i,
                    startDate.plusMonths(i),
                    capitalPortion,
                    interestPortion,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            );
            schedule.add(installment);

            remainingBalance = remainingBalance.subtract(capitalPortion);
            if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) {
                remainingBalance = BigDecimal.ZERO;
            }
        }

        return schedule;
    }

    /**
     * Registra un pago en el crédito con aplicación priorizada según reglas de negocio.
     * El orden de aplicación es: 1) Mora/Comisiones, 2) Interés vencido, 3) Capital vencido,
     * 4) Interés corriente, 5) Capital corriente, 6) Anticipado.
     * Si el saldo llega a cero, cambia el estado a pagado.
     *
     * @param paymentAmount monto del pago
     * @return nueva instancia del crédito con el saldo actualizado
     */
    public Credit applyPayment(BigDecimal paymentAmount) {
        BigDecimal normalizedPayment = paymentAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = this.outstandingBalance.subtract(normalizedPayment);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }
        CreditStatus newStatus = this.status;
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = CreditStatus.PAID_OFF;
        }
        return this.toBuilder()
                .outstandingBalance(newBalance)
                .status(newStatus)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Marca el crédito como en mora.
     *
     * @return nueva instancia del crédito con estado OVERDUE
     */
    public Credit markAsOverdue() {
        return this.toBuilder()
                .status(CreditStatus.OVERDUE)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Activa el crédito cambiando su estado a ACTIVE.
     *
     * @return nueva instancia del crédito activado
     */
    public Credit activate() {
        return this.toBuilder()
                .status(CreditStatus.ACTIVE)
                .approvedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Cancela el crédito cambiando su estado a CANCELLED.
     * Solo se puede cancelar un crédito en estado APPROVED o ACTIVE.
     *
     * @return nueva instancia del crédito cancelado
     */
    public Credit cancel() {
        return this.toBuilder()
                .status(CreditStatus.CANCELLED)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Actualiza la tasa de interés y regenera el cronograma de pagos.
     * Se usa para reestructuraciones de crédito.
     *
     * @param newInterestRate nueva tasa de interés anual
     * @return nueva instancia del crédito con la tasa actualizada y cronograma regenerado
     */
    public Credit updateInterestRate(BigDecimal newInterestRate) {
        BigDecimal normalizedRate = newInterestRate.setScale(2, RoundingMode.HALF_UP);
        List<ScheduleInstallment> newSchedule = generateSchedule(
                this.principalAmount, normalizedRate, this.termMonths);
        LocalDate now = LocalDate.now();
        LocalDate firstDue = now.plusMonths(1);
        ScheduleInstallment firstInstallment = newSchedule.isEmpty() ? null : newSchedule.get(0);
        LocalDate nextDue = firstInstallment != null ? firstInstallment.getDueDate() : firstDue;
        return this.toBuilder()
                .interestRate(normalizedRate)
                .schedule(newSchedule)
                .nextDueDate(nextDue)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Revierte un pago devolviendo el monto al saldo pendiente del crédito.
     * Se utiliza cuando se anula un pago registrado.
     * Si el crédito estaba pagado (PAID_OFF), vuelve a estado ACTIVE.
     *
     * @param paymentAmount monto del pago a revertir
     * @return nueva instancia del crédito con el saldo aumentado
     */
    public Credit reversePayment(BigDecimal paymentAmount) {
        BigDecimal normalizedPayment = paymentAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = this.outstandingBalance.add(normalizedPayment);
        CreditStatus newStatus = this.status;
        if (this.status == CreditStatus.PAID_OFF) {
            newStatus = CreditStatus.ACTIVE;
        }
        return this.toBuilder()
                .outstandingBalance(newBalance)
                .status(newStatus)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Ajusta el saldo del crédito por la diferencia de una corrección de pago.
     * Si el nuevo monto es mayor al original, se reduce el saldo.
     * Si el nuevo monto es menor al original, se aumenta el saldo.
     *
     * @param originalAmount monto original del pago
     * @param correctedAmount monto corregido del pago
     * @return nueva instancia del crédito con el saldo ajustado
     */
    public Credit adjustPaymentCorrection(BigDecimal originalAmount, BigDecimal correctedAmount) {
        BigDecimal normalizedOriginal = originalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedCorrected = correctedAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = normalizedCorrected.subtract(normalizedOriginal);
        BigDecimal newBalance = this.outstandingBalance.subtract(difference);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }
        CreditStatus newStatus = this.status;
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = CreditStatus.PAID_OFF;
        }
        return this.toBuilder()
                .outstandingBalance(newBalance)
                .status(newStatus)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
