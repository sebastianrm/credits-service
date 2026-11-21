package com.yanki.credits.infrastructure.persistence.mapper;

import com.yanki.credits.domain.model.ScheduleInstallment;
import com.yanki.credits.infrastructure.persistence.document.ScheduleInstallmentDocument;

/**
 * Traductor estatico entre ScheduleInstallment de dominio y ScheduleInstallmentDocument.
 */
public final class ScheduleInstallmentDocumentMapper {

    private ScheduleInstallmentDocumentMapper() {
    }

    public static ScheduleInstallment toDomain(ScheduleInstallmentDocument doc) {
        return new ScheduleInstallment(
                doc.getInstallmentNumber(),
                doc.getDueDate(),
                doc.getCapitalAmount(),
                doc.getInterestAmount(),
                doc.getFeeAmount(),
                doc.getTotalAmount(),
                doc.getStatus()
        );
    }

    public static ScheduleInstallmentDocument toDocument(ScheduleInstallment installment) {
        return ScheduleInstallmentDocument.builder()
                .installmentNumber(installment.getInstallmentNumber())
                .dueDate(installment.getDueDate())
                .capitalAmount(installment.getCapitalAmount())
                .interestAmount(installment.getInterestAmount())
                .feeAmount(installment.getFeeAmount())
                .totalAmount(installment.getTotalAmount())
                .status(installment.getStatus())
                .build();
    }
}
