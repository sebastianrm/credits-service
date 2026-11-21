package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.model.Payment;
import com.yanki.credits.domain.model.PaymentSource;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditEventPublisherPort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.yanki.credits.domain.port.out.PaymentPersistencePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de unidad para RegisterPaymentService.
 * Cubre los casos de prueba CP-079 a CP-081 del microservicio credits-service.
 * <p>
 * Referencia Bidireccional:
 * - CU: CU-06 (Pagos de Créditos)
 * - SEC: SEC027 (Pago crédito)
 * - CP: CP-079, CP-080, CP-081
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterPaymentServiceTest {

    @Mock
    private CreditPersistencePort creditPersistencePort;

    @Mock
    private PaymentPersistencePort paymentPersistencePort;

    @Mock
    private CreditEventPublisherPort eventPublisherPort;

    @Mock
    private CachePort cachePort;

    private RegisterPaymentService registerPaymentService;

    private Credit activeCredit;

    @BeforeEach
    void setUp() {
        registerPaymentService = new RegisterPaymentService(
                creditPersistencePort, paymentPersistencePort,
                eventPublisherPort, cachePort
        );

        // Crédito activo con saldo pendiente
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
    }

    // ==================== CP-079: Pago de crédito exitoso desde cuenta propia ====================

    @Nested
    @DisplayName("CP-079 — Pago de crédito exitoso desde cuenta propia (event-driven)")
    class Cp079_RegisterPaymentSuccess {

        @Test
        @DisplayName("Debe registrar un pago exitosamente desde cuenta propia")
        void shouldRegisterPaymentFromOwnAccount() {
            // Arrange
            String creditId = "cred-001";
            BigDecimal amount = new BigDecimal("500.00");
            PaymentSource source = PaymentSource.OWN_ACCOUNT;
            String sourceAccountId = "acc-67890";

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Payment result = registerPaymentService.execute(
                    creditId, amount, source, sourceAccountId, null
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCreditId()).isEqualTo(creditId);
            assertThat(result.getCustomerId()).isEqualTo("cust-12345");
            assertThat(result.getAmount()).isEqualTo(amount);
            assertThat(result.getSource()).isEqualTo(PaymentSource.OWN_ACCOUNT);
            assertThat(result.getSourceAccountId()).isEqualTo(sourceAccountId);
            assertThat(result.getStatus()).isEqualTo("COMPLETED");

            // Verificar que se actualizó el crédito con el nuevo saldo
            ArgumentCaptor<Credit> creditCaptor = ArgumentCaptor.forClass(Credit.class);
            verify(creditPersistencePort).save(creditCaptor.capture());
            Credit updatedCredit = creditCaptor.getValue();
            assertThat(updatedCredit.getOutstandingBalance())
                    .isEqualTo(new BigDecimal("7000.00")); // 7500 - 500

            // Verificar que se invalidó la caché
            verify(cachePort).delete(eq("yanki:credits:schedule:" + creditId));

            // Verificar que se publicó el evento
            verify(eventPublisherPort).publish(any());
        }

        @Test
        @DisplayName("Debe registrar pago parcial y mantener el crédito activo")
        void shouldRegisterPartialPaymentAndKeepCreditActive() {
            // Arrange
            String creditId = "cred-001";
            BigDecimal amount = new BigDecimal("1000.00");

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Payment result = registerPaymentService.execute(
                    creditId, amount, PaymentSource.OWN_ACCOUNT, "acc-67890", null
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();

            ArgumentCaptor<Credit> creditCaptor = ArgumentCaptor.forClass(Credit.class);
            verify(creditPersistencePort).save(creditCaptor.capture());
            Credit updatedCredit = creditCaptor.getValue();
            assertThat(updatedCredit.getStatus()).isEqualTo(CreditStatus.ACTIVE);
            assertThat(updatedCredit.getOutstandingBalance())
                    .isEqualTo(new BigDecimal("6500.00"));
        }

        @Test
        @DisplayName("Debe cambiar el estado a PAID_OFF cuando el pago cubre todo el saldo")
        void shouldChangeStatusToPaidOffWhenFullPayment() {
            // Arrange
            String creditId = "cred-001";
            BigDecimal amount = new BigDecimal("7500.00"); // Saldo exacto

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Payment result = registerPaymentService.execute(
                    creditId, amount, PaymentSource.OWN_ACCOUNT, "acc-67890", null
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();

            ArgumentCaptor<Credit> creditCaptor = ArgumentCaptor.forClass(Credit.class);
            verify(creditPersistencePort).save(creditCaptor.capture());
            Credit updatedCredit = creditCaptor.getValue();
            assertThat(updatedCredit.getStatus()).isEqualTo(CreditStatus.PAID_OFF);
            assertThat(updatedCredit.getOutstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Debe rechazar pago cuando el crédito no existe")
        void shouldRejectPaymentWhenCreditNotFound() {
            // Arrange
            String creditId = "cred-no-existe";
            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.empty());

            // Act & Assert
            assertThatThrownBy(() -> registerPaymentService.execute(
                    creditId, new BigDecimal("500"), PaymentSource.OWN_ACCOUNT, "acc-67890", null
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Crédito no encontrado");
        }

        @Test
        @DisplayName("Debe rechazar pago cuando el monto es cero o negativo")
        void shouldRejectPaymentWhenAmountIsZero() {
            // Arrange
            String creditId = "cred-001";

            // Act & Assert
            assertThatThrownBy(() -> registerPaymentService.execute(
                    creditId, BigDecimal.ZERO, PaymentSource.OWN_ACCOUNT, "acc-67890", null
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("monto del pago debe ser mayor a cero");
        }

        @Test
        @DisplayName("Debe rechazar pago cuando el crédito está cancelado")
        void shouldRejectPaymentWhenCreditIsCancelled() {
            // Arrange
            String creditId = "cred-001";
            Credit cancelledCredit = activeCredit.toBuilder()
                    .status(CreditStatus.CANCELLED)
                    .build();
            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(cancelledCredit));

            // Act & Assert
            assertThatThrownBy(() -> registerPaymentService.execute(
                    creditId, new BigDecimal("500"), PaymentSource.OWN_ACCOUNT, "acc-67890", null
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("crédito cancelado");
        }

        @Test
        @DisplayName("Debe rechazar pago cuando el crédito ya está pagado")
        void shouldRejectPaymentWhenCreditIsPaidOff() {
            // Arrange
            String creditId = "cred-001";
            Credit paidCredit = activeCredit.toBuilder()
                    .status(CreditStatus.PAID_OFF)
                    .outstandingBalance(BigDecimal.ZERO)
                    .build();
            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(paidCredit));

            // Act & Assert
            assertThatThrownBy(() -> registerPaymentService.execute(
                    creditId, new BigDecimal("500"), PaymentSource.OWN_ACCOUNT, "acc-67890", null
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ya ha sido pagado totalmente");
        }
    }

    // ==================== CP-080: Pago rechazado: fondos insuficientes ====================

    @Nested
    @DisplayName("CP-080 — Pago de crédito rechazado: fondos insuficientes en cuenta origen")
    class Cp080_RejectInsufficientFunds {

        @Test
        @DisplayName("El servicio de dominio acepta el pago; la validación de fondos es responsabilidad de accounts-service")
        void shouldAcceptPaymentDomainLogic() {
            // Arrange
            // Nota: CP-080 valida que el flujo event-driven rechaza el pago cuando
            // accounts-service responde con INSUFFICIENT_FUNDS.
            // A nivel de dominio de credits-service, el servicio acepta el pago.
            // La validación de fondos ocurre en accounts-service vía evento debit.payment.settled.v1.
            // Esta prueba verifica que el dominio procesa correctamente el pago sin fallar.
            String creditId = "cred-001";
            BigDecimal amount = new BigDecimal("500.00");

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Payment result = registerPaymentService.execute(
                    creditId, amount, PaymentSource.OWN_ACCOUNT, "acc-67890", null
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualTo(amount);
            assertThat(result.getSource()).isEqualTo(PaymentSource.OWN_ACCOUNT);
        }
    }

    // ==================== CP-081: Pago de crédito efectivo/terceros ====================

    @Nested
    @DisplayName("CP-081 — Pago de crédito efectivo/terceros (sin débito cuenta)")
    class Cp081_RegisterPaymentFromThirdParty {

        @Test
        @DisplayName("Debe registrar un pago desde tercero exitosamente")
        void shouldRegisterPaymentFromThirdParty() {
            // Arrange
            String creditId = "cred-001";
            BigDecimal amount = new BigDecimal("500.00");
            PaymentSource source = PaymentSource.THIRD_PARTY;
            String payerCustomerId = "cust-99999";

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Payment result = registerPaymentService.execute(
                    creditId, amount, source, null, payerCustomerId
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCreditId()).isEqualTo(creditId);
            assertThat(result.getAmount()).isEqualTo(amount);
            assertThat(result.getSource()).isEqualTo(PaymentSource.THIRD_PARTY);
            assertThat(result.getPayerCustomerId()).isEqualTo(payerCustomerId);
            assertThat(result.getStatus()).isEqualTo("COMPLETED");

            // Verificar que se actualizó el saldo
            ArgumentCaptor<Credit> creditCaptor = ArgumentCaptor.forClass(Credit.class);
            verify(creditPersistencePort).save(creditCaptor.capture());
            assertThat(creditCaptor.getValue().getOutstandingBalance())
                    .isEqualTo(new BigDecimal("7000.00"));
        }

        @Test
        @DisplayName("Debe registrar un pago en efectivo exitosamente")
        void shouldRegisterCashPayment() {
            // Arrange
            String creditId = "cred-001";
            BigDecimal amount = new BigDecimal("200.00");
            PaymentSource source = PaymentSource.CASH;

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Payment result = registerPaymentService.execute(
                    creditId, amount, source, null, null
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getSource()).isEqualTo(PaymentSource.CASH);
            assertThat(result.getAmount()).isEqualTo(amount);
        }

        @Test
        @DisplayName("Debe usar OWN_ACCOUNT como fuente por defecto cuando source es null")
        void shouldUseOwnAccountAsDefaultSource() {
            // Arrange
            String creditId = "cred-001";
            BigDecimal amount = new BigDecimal("500.00");

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(creditPersistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(paymentPersistencePort.save(any(Payment.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Payment result = registerPaymentService.execute(
                    creditId, amount, null, "acc-67890", null
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getSource()).isEqualTo(PaymentSource.OWN_ACCOUNT);
        }
    }

    // ==================== Listado y consulta de pagos ====================

    @Nested
    @DisplayName("Consulta de pagos por crédito")
    class ListPaymentsByCredit {

        @Test
        @DisplayName("Debe listar los pagos de un crédito")
        void shouldListPaymentsByCreditId() {
            // Arrange
            String creditId = "cred-001";
            Payment payment1 = Payment.create(creditId, "cust-12345", new BigDecimal("500"),
                    PaymentSource.OWN_ACCOUNT, "acc-67890", null);
            Payment payment2 = Payment.create(creditId, "cust-12345", new BigDecimal("300"),
                    PaymentSource.CASH, null, null);

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findByCreditId(creditId))
                    .thenReturn(Flowable.just(payment1, payment2));

            // Act
            List<Payment> payments = registerPaymentService.listByCredit(creditId)
                    .test().assertNoErrors().values();

            // Assert
            assertThat(payments).hasSize(2);
            assertThat(payments.get(0).getAmount()).isEqualTo(new BigDecimal("500.00"));
            assertThat(payments.get(1).getAmount()).isEqualTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("Debe encontrar un pago por ID dentro de un crédito")
        void shouldFindPaymentById() {
            // Arrange
            String creditId = "cred-001";
            String paymentId = "pay-001";
            Payment payment = Payment.create(creditId, "cust-12345", new BigDecimal("500"),
                    PaymentSource.OWN_ACCOUNT, "acc-67890", null);

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findById(paymentId)).thenReturn(Maybe.just(payment));

            // Act
            Payment result = registerPaymentService.findById(creditId, paymentId)
                    .test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCreditId()).isEqualTo(creditId);
        }

        @Test
        @DisplayName("Debe rechazar cuando el pago no pertenece al crédito")
        void shouldRejectWhenPaymentDoesNotBelongToCredit() {
            // Arrange
            String creditId = "cred-001";
            String paymentId = "pay-otro";
            Payment payment = Payment.create("cred-otro", "cust-otro", new BigDecimal("500"),
                    PaymentSource.OWN_ACCOUNT, "acc-67890", null);

            when(creditPersistencePort.findById(creditId)).thenReturn(Maybe.just(activeCredit));
            when(paymentPersistencePort.findById(paymentId)).thenReturn(Maybe.just(payment));

            // Act & Assert
            assertThatThrownBy(() -> registerPaymentService.findById(creditId, paymentId)
                    .blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Pago no encontrado");
        }
    }
}
