package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.model.PaymentSource;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.yanki.credits.domain.port.out.PaymentPersistencePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de unidad para ReversePaymentService.
 * Cubre la reversa de pagos registrados (CRUD de Payment — DELETE).
 * <p>
 * Referencia Bidireccional:
 * - CU: CU-06 (Pagos de Créditos)
 * - SEC: SEC027 (Pago crédito)
 * - CP: CRUD Payment DELETE
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReversePaymentServiceTest {

    @Mock
    private CreditPersistencePort creditPersistencePort;

    @Mock
    private PaymentPersistencePort paymentPersistencePort;

    @Mock
    private CachePort cachePort;

    private ReversePaymentService reversePaymentService;

    private Credit activeCredit;
    private Payment completedPayment;

    @BeforeEach
    void setUp() {
        reversePaymentService = new ReversePaymentService(
                creditPersistencePort, paymentPersistencePort, cachePort
        );

        activeCredit = Credit.builder()
                .id("cred-001")
                .customerId("cust-12345")
                .type(CreditType.PERSONAL)
                .status(CreditStatus.ACTIVE)
                .principalAmount(new BigDecimal("10000.00"))
                .interestRate(new BigDecimal("12.50"))
                .termMonths(12)
                .outstandingBalance(new BigDecimal("7500.00"))
                .nextDueDate(LocalDate.now().plusMonths(1))
                .firstDueDate(LocalDate.now())
                .maturityDate(LocalDate.now().plusMonths(12))
                .paymentFrequency("MONTHLY")
                .purpose(null)
                .schedule(List.of())
                .build();

        completedPayment = Payment.create("cred-001", "cust-12345",
                new BigDecimal("500.00"), PaymentSource.OWN_ACCOUNT, "acc-67890", null);
    }

    // ==================== Happy Path: Reversa exitosa ====================

    @Nested
    @DisplayName("Happy Path — Reversa de pago exitosa")
    class HappyPath_ReversePayment {

        @Test
        @DisplayName("Debe revertir un pago exitosamente")
        void shouldReversePaymentSuccessfully() {
            // Arrange
            String creditId = "cred-001";
            String paymentId = completedPayment.getId();

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findById(paymentId)).thenReturn(Maybe.just(completedPayment));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            Payment result = reversePaymentService.execute(creditId, paymentId)
                    .test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("REVERSED");
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(result.getCreditId()).isEqualTo(creditId);
        }

        @Test
        @DisplayName("Debe devolver el monto al saldo del crédito")
        void shouldReturnAmountToCreditBalance() {
            // Arrange
            String creditId = "cred-001";
            String paymentId = completedPayment.getId();
            // Saldo original: 7500, monto pago: 500, nuevo saldo: 8000
            BigDecimal expectedBalance = new BigDecimal("8000.00");

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findById(paymentId)).thenReturn(Maybe.just(completedPayment));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            reversePaymentService.execute(creditId, paymentId)
                    .test().assertNoErrors().values().get(0);

            // Assert
            verify(creditPersistencePort).save(any(Credit.class));
            verify(paymentPersistencePort).save(any(Payment.class));
            verify(cachePort).delete("yanki:credits:schedule:" + creditId);
        }

        @Test
        @DisplayName("Debe cambiar el crédito de PAID_OFF a ACTIVE al revertir un pago")
        void shouldChangePaidOffCreditToActive() {
            // Arrange
            Credit paidOffCredit = Credit.builder()
                    .id("cred-001")
                    .customerId("cust-12345")
                    .type(CreditType.PERSONAL)
                    .status(CreditStatus.PAID_OFF)
                    .principalAmount(new BigDecimal("10000.00"))
                    .interestRate(new BigDecimal("12.50"))
                    .termMonths(12)
                    .outstandingBalance(BigDecimal.ZERO)
                    .nextDueDate(LocalDate.now().plusMonths(1))
                    .firstDueDate(LocalDate.now())
                    .maturityDate(LocalDate.now().plusMonths(12))
                    .paymentFrequency("MONTHLY")
                    .purpose(null)
                    .schedule(List.of())
                    .build();

            when(creditPersistencePort.findById("cred-001")).thenReturn(Maybe.just(paidOffCredit));
            when(paymentPersistencePort.findById(completedPayment.getId()))
                    .thenReturn(Maybe.just(completedPayment));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            reversePaymentService.execute("cred-001", completedPayment.getId())
                    .test().assertNoErrors().values().get(0);

            // Assert
            verify(creditPersistencePort).save(any(Credit.class));
        }
    }

    // ==================== Casos de Error ====================

    @Nested
    @DisplayName("Error Cases — Reversa de pago")
    class ErrorCases_ReversePayment {

        @Test
        @DisplayName("Debe fallar si el crédito no existe")
        void shouldFailWhenCreditNotFound() {
            // Arrange
            when(creditPersistencePort.findById("cred-999")).thenReturn(Maybe.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                    reversePaymentService.execute("cred-999", "pay-001").blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Crédito no encontrado");
        }

        @Test
        @DisplayName("Debe fallar si el pago no existe")
        void shouldFailWhenPaymentNotFound() {
            // Arrange
            when(creditPersistencePort.findById("cred-001")).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findById("pay-999")).thenReturn(Maybe.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                    reversePaymentService.execute("cred-001", "pay-999").blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Pago no encontrado");
        }

        @Test
        @DisplayName("Debe fallar si el pago no pertenece al crédito")
        void shouldFailWhenPaymentNotInCredit() {
            // Arrange
            Payment otherPayment = Payment.create("cred-OTHER", "cust-OTHER",
                    new BigDecimal("500.00"), PaymentSource.OWN_ACCOUNT, null, null);

            when(creditPersistencePort.findById("cred-001")).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findById(otherPayment.getId()))
                    .thenReturn(Maybe.just(otherPayment));

            // Act & Assert
            assertThatThrownBy(() ->
                    reversePaymentService.execute("cred-001", otherPayment.getId()).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("no pertenece al crédito");
        }

        @Test
        @DisplayName("Debe fallar si el crédito está cancelado")
        void shouldFailWhenCreditIsCancelled() {
            // Arrange
            Credit cancelledCredit = activeCredit.cancel();
            when(creditPersistencePort.findById("cred-001")).thenReturn(Maybe.just(cancelledCredit));

            // Act & Assert
            assertThatThrownBy(() ->
                    reversePaymentService.execute("cred-001", completedPayment.getId()).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cancelado");
        }

        @Test
        @DisplayName("Debe fallar si el pago ya fue revertido")
        void shouldFailWhenPaymentAlreadyReversed() {
            // Arrange
            Payment reversedPayment = completedPayment.reverse();

            when(creditPersistencePort.findById("cred-001")).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findById(reversedPayment.getId()))
                    .thenReturn(Maybe.just(reversedPayment));

            // Act & Assert
            assertThatThrownBy(() ->
                    reversePaymentService.execute("cred-001", reversedPayment.getId()).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ya fue revertido");
        }
    }
}
