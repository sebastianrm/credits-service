package com.yanki.credits.infrastructure.persistence.mapper;

import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.infrastructure.persistence.document.PaymentDocument;

/**
 * Traductor estatico entre Payment de dominio y PaymentDocument de MongoDB.
 */
public final class PaymentDocumentMapper {

    private PaymentDocumentMapper() {
    }

    /**
     * Convierte un documento MongoDB a entidad de dominio.
     *
     * @param doc documento de MongoDB
     * @return entidad de dominio
     */
    public static Payment toDomain(PaymentDocument doc) {
        return new Payment(
                doc.getId(),
                doc.getCreditId(),
                doc.getCustomerId(),
                doc.getAmount(),
                doc.getCapitalPortion(),
                doc.getInterestPortion(),
                doc.getFeePortion(),
                doc.getInstallmentNumber(),
                doc.getDueDate(),
                doc.getPaidAt(),
                com.yanki.credits.domain.model.PaymentSource.valueOf(doc.getSource()),
                doc.getSourceAccountId(),
                doc.getPayerCustomerId(),
                doc.getStatus(),
                doc.getCreatedAt()
        );
    }

    /**
     * Convierte una entidad de dominio a documento MongoDB.
     *
     * @param payment entidad de dominio
     * @return documento de MongoDB
     */
    public static PaymentDocument toDocument(Payment payment) {
        return PaymentDocument.builder()
                .id(payment.getId())
                .creditId(payment.getCreditId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .capitalPortion(payment.getCapitalPortion())
                .interestPortion(payment.getInterestPortion())
                .feePortion(payment.getFeePortion())
                .installmentNumber(payment.getInstallmentNumber())
                .dueDate(payment.getDueDate())
                .paidAt(payment.getPaidAt())
                .source(payment.getSource().name())
                .sourceAccountId(payment.getSourceAccountId())
                .payerCustomerId(payment.getPayerCustomerId())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
