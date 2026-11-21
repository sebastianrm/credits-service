package com.yanki.credits.domain.service;

import com.yanki.credits.domain.event.CreditEvent;
import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CustomerProfilePort;
import com.yanki.credits.domain.port.out.CustomerProfileView;
import com.yanki.credits.domain.port.out.CustomerRiskPort;
import com.yanki.credits.domain.port.out.CustomerRiskView;
import com.yanki.credits.domain.port.out.CreditEventPublisherPort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Pruebas de unidad para CreateCreditService.
 * Cubre los casos de prueba CP-075 a CP-078 del microservicio credits-service.
 * <p>
 * Referencia Bidireccional:
 * - CU: CU-03 (Gestión de Créditos)
 * - SEC: SEC025 (Apertura crédito personal), SEC026 (Apertura crédito empresarial)
 * - CP: CP-075, CP-076, CP-077, CP-078
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateCreditServiceTest {

    @Mock
    private CreditPersistencePort persistencePort;

    @Mock
    private CreditEventPublisherPort eventPublisherPort;

    @Mock
    private CustomerProfilePort customerProfilePort;

    @Mock
    private CustomerRiskPort customerRiskPort;

    @Mock
    private CachePort cachePort;

    private CreateCreditService createCreditService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        createCreditService = new CreateCreditService(
                persistencePort, eventPublisherPort,
                customerProfilePort, customerRiskPort,
                cachePort, objectMapper
        );
    }

    // ==================== CP-075: Apertura de crédito personal exitosa ====================

    @Nested
    @DisplayName("CP-075 — Apertura de crédito personal exitosa")
    class Cp075_CreatePersonalCreditSuccess {

        @Test
        @DisplayName("Debe crear un crédito personal exitosamente cuando el cliente es válido")
        void shouldCreatePersonalCreditSuccessfully() {
            // Arrange
            String customerId = "cust-12345";
            BigDecimal amount = new BigDecimal("5000.00");
            BigDecimal interestRate = new BigDecimal("12.50");
            int termMonths = 24;

            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.countByCustomerIdAndType(eq(customerId), eq("PERSONAL")))
                    .thenReturn(Single.just(0L));
            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation -> {
                Credit credit = invocation.getArgument(0);
                return Single.just(credit);
            });
            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Credit result = createCreditService.execute(
                    customerId, "PERSONAL", amount, interestRate, termMonths
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCustomerId()).isEqualTo(customerId);
            assertThat(result.getType()).isEqualTo(CreditType.PERSONAL);
            assertThat(result.getStatus()).isEqualTo(CreditStatus.APPROVED);
            assertThat(result.getPrincipalAmount()).isEqualTo(amount);
            assertThat(result.getInterestRate()).isEqualTo(interestRate);
            assertThat(result.getTermMonths()).isEqualTo(termMonths);
            assertThat(result.getOutstandingBalance()).isEqualTo(amount);
            assertThat(result.getSchedule()).isNotEmpty();
            assertThat(result.getSchedule()).hasSize(termMonths);
            assertThat(result.getFirstDueDate()).isEqualTo(LocalDate.now().plusMonths(1));
            assertThat(result.getMaturityDate()).isEqualTo(LocalDate.now().plusMonths(termMonths));

            // Verificar que se cacheó el cronograma (asíncrono, no verificamos en test de unidad)
            // La caché se establece en un hilo separado; se valida en pruebas de integración

            // Verificar que se publicó el evento
            ArgumentCaptor<CreditEvent> eventCaptor = ArgumentCaptor.forClass(CreditEvent.class);
            verify(eventPublisherPort).publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isNotNull();
        }

        @Test
        @DisplayName("Debe rechazar crédito cuando el cliente no existe")
        void shouldRejectWhenCustomerNotFound() {
            // Arrange
            String customerId = "cust-no-existe";

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.empty());

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), new BigDecimal("12.5"), 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cliente no encontrado");
        }

        @Test
        @DisplayName("Debe rechazar crédito cuando el cliente está inactivo")
        void shouldRejectWhenCustomerInactive() {
            // Arrange
            String customerId = "cust-inactivo";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "INACTIVE", LocalDateTime.now()
            );
            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), new BigDecimal("12.5"), 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("no se encuentra activo");
        }

        @Test
        @DisplayName("Debe rechazar crédito personal cuando cliente es ENTERPRISE")
        void shouldRejectPersonalCreditForEnterpriseCustomer() {
            // Arrange
            String customerId = "cust-enterprise";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "ENTERPRISE", "BUSINESS", "ACTIVE", LocalDateTime.now()
            );
            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), new BigDecimal("12.5"), 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ENTERPRISE no pueden solicitar créditos PERSONALES");
        }

        @Test
        @DisplayName("Debe rechazar crédito cuando el monto es menor al mínimo")
        void shouldRejectWhenAmountBelowMinimum() {
            // Arrange
            String customerId = "cust-12345";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.countByCustomerIdAndType(eq(customerId), eq("PERSONAL")))
                    .thenReturn(Single.just(0L));

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("50"), new BigDecimal("12.5"), 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("monto del crédito debe ser mayor a S/ 100.00");
        }

        @Test
        @DisplayName("Debe rechazar crédito cuando el monto excede el máximo")
        void shouldRejectWhenAmountExceedsMaximum() {
            // Arrange
            String customerId = "cust-12345";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.countByCustomerIdAndType(eq(customerId), eq("PERSONAL")))
                    .thenReturn(Single.just(0L));

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("600000"), new BigDecimal("12.5"), 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("monto excede el límite máximo");
        }

        @Test
        @DisplayName("Debe rechazar crédito cuando la tasa de interés es cero o negativa")
        void shouldRejectWhenInterestRateIsZero() {
            // Arrange
            String customerId = "cust-12345";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.countByCustomerIdAndType(eq(customerId), eq("PERSONAL")))
                    .thenReturn(Single.just(0L));

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), BigDecimal.ZERO, 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tasa de interés debe ser mayor a cero");
        }

        @Test
        @DisplayName("Debe rechazar crédito cuando el plazo excede el máximo permitido")
        void shouldRejectWhenTermExceedsMaximum() {
            // Arrange
            String customerId = "cust-12345";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.countByCustomerIdAndType(eq(customerId), eq("PERSONAL")))
                    .thenReturn(Single.just(0L));

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), new BigDecimal("12.5"), 400
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("plazo debe estar entre 1 y 360 meses");
        }
    }

    // ==================== CP-076: Apertura crédito personal rechazada: ya tiene crédito ====================

    @Nested
    @DisplayName("CP-076 — Apertura crédito personal rechazada: ya tiene crédito personal activo")
    class Cp076_RejectDuplicatePersonalCredit {

        @Test
        @DisplayName("Debe rechazar segundo crédito personal cuando ya existe uno activo")
        void shouldRejectSecondPersonalCredit() {
            // Arrange
            String customerId = "cust-12345";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.countByCustomerIdAndType(eq(customerId), eq("PERSONAL")))
                    .thenReturn(Single.just(1L)); // Ya tiene 1 crédito personal

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), new BigDecimal("12.5"), 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ya tiene un crédito personal activo");
        }
    }

    // ==================== CP-077: Apertura crédito rechazada: cliente con deuda vencida ====================

    @Nested
    @DisplayName("CP-077 — Apertura crédito rechazada: cliente con deuda vencida")
    class Cp077_RejectCustomerWithOverdueDebt {

        @Test
        @DisplayName("Debe rechazar crédito cuando el cliente tiene deuda vencida mayor a 30 días")
        void shouldRejectWhenCustomerHasOverdueDebt() {
            // Arrange
            String customerId = "cust-deuda-vencida";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, true, LocalDateTime.now()); // hasOverdueDebt = true

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));

            // Act & Assert
            assertThatThrownBy(() -> createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), new BigDecimal("12.5"), 12
            ).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("deuda vencida mayor a 30 días");
        }

        @Test
        @DisplayName("Debe permitir crédito cuando no existe vista de riesgo (cliente sin historial)")
        void shouldAllowWhenNoRiskViewExists() {
            // Arrange
            String customerId = "cust-nuevo";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "PERSONAL", "STANDARD", "ACTIVE", LocalDateTime.now()
            );

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.empty()); // Sin vista de riesgo
            when(persistencePort.countByCustomerIdAndType(eq(customerId), eq("PERSONAL")))
                    .thenReturn(Single.just(0L));
            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());

            // Act
            Credit result = createCreditService.execute(
                    customerId, "PERSONAL", new BigDecimal("5000"), new BigDecimal("12.5"), 12
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCustomerId()).isEqualTo(customerId);
        }
    }

    // ==================== CP-078: Apertura crédito empresarial exitosa ====================

    @Nested
    @DisplayName("CP-078 — Apertura crédito empresarial exitosa (múltiples permitidos)")
    class Cp078_CreateBusinessCreditSuccess {

        @Test
        @DisplayName("Debe crear un crédito empresarial exitosamente")
        void shouldCreateBusinessCreditSuccessfully() {
            // Arrange
            String customerId = "cust-empresa-001";
            BigDecimal amount = new BigDecimal("50000.00");
            BigDecimal interestRate = new BigDecimal("10.50");
            int termMonths = 36;
            String purpose = "Capital de trabajo";

            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "ENTERPRISE", "BUSINESS", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());
            when(persistencePort.countByCustomerIdAndType(anyString(), anyString()))
                    .thenReturn(Single.just(0L));

            // Act
            Credit result = createCreditService.execute(
                    customerId, "BUSINESS", amount, interestRate, termMonths, purpose
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getCustomerId()).isEqualTo(customerId);
            assertThat(result.getType()).isEqualTo(CreditType.BUSINESS);
            assertThat(result.getStatus()).isEqualTo(CreditStatus.APPROVED);
            assertThat(result.getPrincipalAmount()).isEqualTo(amount);
            assertThat(result.getInterestRate()).isEqualTo(interestRate);
            assertThat(result.getTermMonths()).isEqualTo(termMonths);
            assertThat(result.getPurpose()).isEqualTo(purpose);
            assertThat(result.getSchedule()).hasSize(termMonths);
        }

        @Test
        @DisplayName("Debe permitir múltiples créditos empresariales para el mismo cliente")
        void shouldAllowMultipleBusinessCredits() {
            // Arrange
            String customerId = "cust-empresa-001";
            CustomerProfileView profile = new CustomerProfileView(
                    customerId, "ENTERPRISE", "BUSINESS", "ACTIVE", LocalDateTime.now()
            );
            CustomerRiskView risk = new CustomerRiskView(customerId, false, LocalDateTime.now());

            when(customerProfilePort.findByCustomerId(customerId)).thenReturn(Maybe.just(profile));
            when(customerRiskPort.findByCustomerId(customerId)).thenReturn(Maybe.just(risk));
            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());
            when(persistencePort.countByCustomerIdAndType(anyString(), anyString()))
                    .thenReturn(Single.just(0L));

            // Act — Crear primer crédito
            Credit first = createCreditService.execute(
                    customerId, "BUSINESS", new BigDecimal("30000"), new BigDecimal("10.5"), 24
            ).test().assertNoErrors().values().get(0);

            // Act — Crear segundo crédito (debería permitirse)
            Credit second = createCreditService.execute(
                    customerId, "BUSINESS", new BigDecimal("20000"), new BigDecimal("11.0"), 18
            ).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first.getId()).isNotEqualTo(second.getId());
        }
    }
}
