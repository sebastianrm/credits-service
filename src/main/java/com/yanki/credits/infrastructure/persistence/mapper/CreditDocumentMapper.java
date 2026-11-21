package com.yanki.credits.infrastructure.persistence.mapper;

import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.ScheduleInstallment;
import com.yanki.credits.infrastructure.persistence.document.CreditDocument;
import com.yanki.credits.infrastructure.persistence.document.ScheduleInstallmentDocument;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Traductor estatico entre Credit de dominio y CreditDocument de MongoDB.
 */
public final class CreditDocumentMapper {

    private CreditDocumentMapper() {
    }

    /**
     * Convierte un documento MongoDB a entidad de dominio.
     *
     * @param doc documento de MongoDB
     * @return entidad de dominio
     */
    public static Credit toDomain(CreditDocument doc) {
        List<ScheduleInstallment> schedule = null;
        if (doc.getSchedule() != null) {
            schedule = doc.getSchedule().stream().map(ScheduleInstallmentDocumentMapper::toDomain)
                    .collect(Collectors.toList());
        }
        return new Credit(
                doc.getId(),
                doc.getCustomerId(),
                com.yanki.credits.domain.model.CreditType.valueOf(doc.getType()),
                com.yanki.credits.domain.model.CreditStatus.valueOf(doc.getStatus()),
                doc.getPrincipalAmount(),
                doc.getInterestRate(),
                doc.getTermMonths(),
                doc.getOutstandingBalance(),
                doc.getNextDueDate(),
                doc.getFirstDueDate(),
                doc.getMaturityDate(),
                doc.getPaymentFrequency(),
                doc.getPurpose(),
                schedule,
                doc.getApprovedAt(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    /**
     * Convierte una entidad de dominio a documento MongoDB.
     *
     * @param credit entidad de dominio
     * @return documento de MongoDB
     */
    public static CreditDocument toDocument(Credit credit) {
        List<ScheduleInstallmentDocument> schedule = null;
        if (credit.getSchedule() != null) {
            schedule = credit.getSchedule().stream()
                    .map(ScheduleInstallmentDocumentMapper::toDocument)
                    .collect(Collectors.toList());
        }
        return CreditDocument.builder()
                .id(credit.getId())
                .customerId(credit.getCustomerId())
                .type(credit.getType().name())
                .status(credit.getStatus().name())
                .principalAmount(credit.getPrincipalAmount())
                .interestRate(credit.getInterestRate())
                .termMonths(credit.getTermMonths())
                .outstandingBalance(credit.getOutstandingBalance())
                .nextDueDate(credit.getNextDueDate())
                .firstDueDate(credit.getFirstDueDate())
                .maturityDate(credit.getMaturityDate())
                .paymentFrequency(credit.getPaymentFrequency())
                .purpose(credit.getPurpose())
                .schedule(schedule)
                .approvedAt(credit.getApprovedAt())
                .createdAt(credit.getCreatedAt())
                .updatedAt(credit.getUpdatedAt())
                .build();
    }
}
