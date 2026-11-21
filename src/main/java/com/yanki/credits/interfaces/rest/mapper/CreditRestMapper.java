package com.yanki.credits.interfaces.rest.mapper;

import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.model.ScheduleInstallment;
import com.yanki.credits.interfaces.rest.dto.CreditResponseDto;
import com.yanki.credits.interfaces.rest.dto.PaymentResponseDto;
import com.yanki.credits.interfaces.rest.dto.ScheduleInstallmentResponseDto;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Traductor estatico entre modelos de dominio y DTOs REST.
 */
public final class CreditRestMapper {

    private CreditRestMapper() {
    }

    /**
     * Convierte un credito de dominio a DTO de respuesta REST.
     *
     * @param credit entidad de dominio
     * @return DTO de respuesta
     */
    public static CreditResponseDto toResponse(Credit credit) {
        List<ScheduleInstallmentResponseDto> schedule = null;
        if (credit.getSchedule() != null) {
            schedule = credit.getSchedule().stream()
                    .map(CreditRestMapper::toScheduleResponse)
                    .collect(Collectors.toList());
        }
        return new CreditResponseDto(
                credit.getId(),
                credit.getCustomerId(),
                credit.getType().name(),
                credit.getStatus().name(),
                credit.getPrincipalAmount(),
                credit.getInterestRate(),
                credit.getTermMonths(),
                credit.getOutstandingBalance(),
                credit.getNextDueDate(),
                credit.getFirstDueDate(),
                credit.getMaturityDate(),
                credit.getPaymentFrequency(),
                credit.getPurpose(),
                schedule,
                credit.getApprovedAt(),
                credit.getCreatedAt(),
                credit.getUpdatedAt()
        );
    }

    /**
     * Convierte un pago de dominio a DTO de respuesta REST.
     *
     * @param payment entidad de dominio
     * @return DTO de respuesta
     */
    public static PaymentResponseDto toPaymentResponse(Payment payment) {
        return new PaymentResponseDto(
                payment.getId(),
                payment.getCreditId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCapitalPortion(),
                payment.getInterestPortion(),
                payment.getFeePortion(),
                payment.getInstallmentNumber(),
                payment.getDueDate(),
                payment.getPaidAt(),
                payment.getSource().name(),
                payment.getSourceAccountId(),
                payment.getStatus()
        );
    }

    private static ScheduleInstallmentResponseDto toScheduleResponse(ScheduleInstallment installment) {
        return new ScheduleInstallmentResponseDto(
                installment.getInstallmentNumber(),
                installment.getDueDate(),
                installment.getCapitalAmount(),
                installment.getInterestAmount(),
                installment.getFeeAmount(),
                installment.getTotalAmount(),
                installment.getStatus()
        );
    }
}
