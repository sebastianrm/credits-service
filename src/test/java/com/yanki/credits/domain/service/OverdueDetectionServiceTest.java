package com.yanki.credits.domain.service;

import com.yanki.credits.domain.event.CreditEvent;
import com.yanki.credits.domain.event.CreditOverdueEvent;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditEventPublisherPort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.yanki.credits.domain.port.out.OverdueDetectionPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de unidad para OverdueDetectionService.
 * Cubre el caso de prueba CP-082 del microservicio credits-service.
 * <p>
 * Referencia Bidireccional:
 * - CU: CU-03 (Gestión de Créditos)
 * - SEC: SEC028 (Job mora bloqueo transversal)
 * - CP: CP-082
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverdueDetectionServiceTest {

    @Mock
    private CreditPersistencePort persistencePort;

    @Mock
    private CreditEventPublisherPort eventPublisherPort;

    @Mock
    private CachePort cachePort;

    @Mock
    private OverdueDetectionPort overdueDetectionPort;

    private OverdueDetectionService overdueDetectionService;

    @BeforeEach
    void setUp() {
        overdueDetectionService = new OverdueDetectionService(
                persistencePort, eventPublisherPort,
                cachePort, overdueDetectionPort
        );
    }

    // ==================== CP-082: Job diario mora >30 días ====================

    @Nested
    @DisplayName("CP-082 — Job diario mora >30 días: detección y bloqueo transversal")
    class Cp082_OverdueDetectionJob {

        @Test
        @DisplayName("Debe detectar y marcar como OVERDUE los créditos con mora mayor a 30 días")
        void shouldDetectAndMarkOverdueCredits() {
            // Arrange
            // Crédito con nextDueDate hace 45 días (mora > 30 días)
            Credit overdueCredit = Credit.builder()
                    .id("cred-overdue-001")
                    .customerId("cust-12345")
                    .type(CreditType.PERSONAL)
                    .status(CreditStatus.ACTIVE)
                    .principalAmount(new BigDecimal("10000.00"))
                    .interestRate(new BigDecimal("12.50"))
                    .termMonths(12)
                    .outstandingBalance(new BigDecimal("7500.00"))
                    .nextDueDate(LocalDate.now().minusDays(45)) // 45 días en mora
                    .firstDueDate(LocalDate.now().minusMonths(6))
                    .maturityDate(LocalDate.now().plusMonths(6))
                    .paymentFrequency("MONTHLY")
                    .purpose(null)
                    .schedule(List.of())
                    .build();

            when(persistencePort.findOverdueCredits())
                    .thenReturn(Flowable.just(overdueCredit));
            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            overdueDetectionService.executeOverdueDetection()
                    .test().assertNoErrors().assertComplete();

            // Assert
            // Verificar que se guardó el crédito con estado OVERDUE
            ArgumentCaptor<Credit> creditCaptor = ArgumentCaptor.forClass(Credit.class);
            verify(persistencePort).save(creditCaptor.capture());
            Credit savedCredit = creditCaptor.getValue();
            assertThat(savedCredit.getStatus()).isEqualTo(CreditStatus.OVERDUE);
            assertThat(savedCredit.getId()).isEqualTo("cred-overdue-001");

            // Verificar que se publicó el evento de mora
            ArgumentCaptor<CreditEvent> eventCaptor = ArgumentCaptor.forClass(CreditEvent.class);
            verify(eventPublisherPort).publish(eventCaptor.capture());
            CreditOverdueEvent event = (CreditOverdueEvent) eventCaptor.getValue();
            assertThat(event.creditId()).isEqualTo("cred-overdue-001");
            assertThat(event.customerId()).isEqualTo("cust-12345");
            assertThat(event.daysOverdue()).isGreaterThan(30);

            // Verificar que se invalidó la caché
            verify(cachePort).delete(eq("yanki:credits:schedule:cred-overdue-001"));
        }

        @Test
        @DisplayName("Debe saltar los créditos que ya están en estado OVERDUE")
        void shouldSkipAlreadyOverdueCredits() {
            // Arrange
            Credit alreadyOverdueCredit = Credit.builder()
                    .id("cred-already-overdue")
                    .customerId("cust-12345")
                    .type(CreditType.PERSONAL)
                    .status(CreditStatus.OVERDUE) // Ya está en mora
                    .principalAmount(new BigDecimal("10000.00"))
                    .interestRate(new BigDecimal("12.50"))
                    .termMonths(12)
                    .outstandingBalance(new BigDecimal("7500.00"))
                    .nextDueDate(LocalDate.now().minusDays(60))
                    .firstDueDate(LocalDate.now().minusMonths(6))
                    .maturityDate(LocalDate.now().plusMonths(6))
                    .paymentFrequency("MONTHLY")
                    .purpose(null)
                    .schedule(List.of())
                    .build();

            when(persistencePort.findOverdueCredits())
                    .thenReturn(Flowable.just(alreadyOverdueCredit));

            // Act
            overdueDetectionService.executeOverdueDetection()
                    .test().assertNoErrors().assertComplete();

            // Assert
            // No debería guardar ni publicar evento para créditos ya en mora
            verify(persistencePort).findOverdueCredits();
            // No se llama a save porque se salta
        }

        @Test
        @DisplayName("Debe saltar los créditos con mora menor o igual a 30 días")
        void shouldSkipCreditsWithLessThan30DaysOverdue() {
            // Arrange
            Credit slightOverdueCredit = Credit.builder()
                    .id("cred-slight-overdue")
                    .customerId("cust-12345")
                    .type(CreditType.PERSONAL)
                    .status(CreditStatus.ACTIVE)
                    .principalAmount(new BigDecimal("10000.00"))
                    .interestRate(new BigDecimal("12.50"))
                    .termMonths(12)
                    .outstandingBalance(new BigDecimal("7500.00"))
                    .nextDueDate(LocalDate.now().minusDays(15)) // Solo 15 días en mora
                    .firstDueDate(LocalDate.now().minusMonths(6))
                    .maturityDate(LocalDate.now().plusMonths(6))
                    .paymentFrequency("MONTHLY")
                    .purpose(null)
                    .schedule(List.of())
                    .build();

            when(persistencePort.findOverdueCredits())
                    .thenReturn(Flowable.just(slightOverdueCredit));

            // Act
            overdueDetectionService.executeOverdueDetection()
                    .test().assertNoErrors().assertComplete();

            // Assert
            // No debería guardar ni publicar evento
            verify(persistencePort).findOverdueCredits();
        }

        @Test
        @DisplayName("Debe procesar múltiples créditos en mora en una sola ejecución")
        void shouldProcessMultipleOverdueCredits() {
            // Arrange
            Credit credit1 = Credit.builder()
                    .id("cred-001")
                    .customerId("cust-001")
                    .type(CreditType.PERSONAL)
                    .status(CreditStatus.ACTIVE)
                    .principalAmount(new BigDecimal("5000.00"))
                    .interestRate(new BigDecimal("12.50"))
                    .termMonths(12)
                    .outstandingBalance(new BigDecimal("3000.00"))
                    .nextDueDate(LocalDate.now().minusDays(45))
                    .firstDueDate(LocalDate.now().minusMonths(6))
                    .maturityDate(LocalDate.now().plusMonths(6))
                    .paymentFrequency("MONTHLY")
                    .purpose(null)
                    .schedule(List.of())
                    .build();

            Credit credit2 = Credit.builder()
                    .id("cred-002")
                    .customerId("cust-002")
                    .type(CreditType.BUSINESS)
                    .status(CreditStatus.ACTIVE)
                    .principalAmount(new BigDecimal("20000.00"))
                    .interestRate(new BigDecimal("10.00"))
                    .termMonths(24)
                    .outstandingBalance(new BigDecimal("15000.00"))
                    .nextDueDate(LocalDate.now().minusDays(60))
                    .firstDueDate(LocalDate.now().minusMonths(12))
                    .maturityDate(LocalDate.now().plusMonths(12))
                    .paymentFrequency("MONTHLY")
                    .purpose("Capital de trabajo")
                    .schedule(List.of())
                    .build();

            when(persistencePort.findOverdueCredits())
                    .thenReturn(Flowable.just(credit1, credit2));
            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            overdueDetectionService.executeOverdueDetection()
                    .test().assertNoErrors().assertComplete();

            // Assert
            // Debería guardar ambos créditos
            ArgumentCaptor<Credit> creditCaptor = ArgumentCaptor.forClass(Credit.class);
            verify(persistencePort, org.mockito.Mockito.times(2)).save(creditCaptor.capture());
            List<Credit> savedCredits = creditCaptor.getAllValues();
            assertThat(savedCredits).hasSize(2);
            assertThat(savedCredits).allMatch(c -> c.getStatus() == CreditStatus.OVERDUE);

            // Debería publicar 2 eventos
            verify(eventPublisherPort, org.mockito.Mockito.times(2)).publish(any());
        }

        @Test
        @DisplayName("Debe manejar correctamente cuando no hay créditos en mora")
        void shouldHandleNoOverdueCredits() {
            // Arrange
            when(persistencePort.findOverdueCredits())
                    .thenReturn(Flowable.empty());

            // Act
            overdueDetectionService.executeOverdueDetection()
                    .test().assertNoErrors().assertComplete();

            // Assert
            verify(persistencePort).findOverdueCredits();
            // No se guardan ni publican eventos
        }

        @Test
        @DisplayName("Debe publicar evento con los datos correctos del crédito en mora")
        void shouldPublishOverdueEventWithCorrectData() {
            // Arrange
            Credit overdueCredit = Credit.builder()
                    .id("cred-event-test")
                    .customerId("cust-event-test")
                    .type(CreditType.PERSONAL)
                    .status(CreditStatus.ACTIVE)
                    .principalAmount(new BigDecimal("10000.00"))
                    .interestRate(new BigDecimal("12.50"))
                    .termMonths(12)
                    .outstandingBalance(new BigDecimal("5000.00"))
                    .nextDueDate(LocalDate.now().minusDays(40))
                    .firstDueDate(LocalDate.now().minusMonths(6))
                    .maturityDate(LocalDate.now().plusMonths(6))
                    .paymentFrequency("MONTHLY")
                    .purpose(null)
                    .schedule(List.of())
                    .build();

            when(persistencePort.findOverdueCredits())
                    .thenReturn(Flowable.just(overdueCredit));
            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            overdueDetectionService.executeOverdueDetection()
                    .test().assertNoErrors().assertComplete();

            // Assert
            ArgumentCaptor<CreditOverdueEvent> eventCaptor = ArgumentCaptor.forClass(CreditOverdueEvent.class);
            verify(eventPublisherPort).publish(eventCaptor.capture());
            CreditOverdueEvent event = eventCaptor.getValue();

            assertThat(event.creditId()).isEqualTo("cred-event-test");
            assertThat(event.customerId()).isEqualTo("cust-event-test");
            assertThat(event.outstandingAmount()).isEqualTo(new BigDecimal("5000.00"));
            assertThat(event.daysOverdue()).isEqualTo(40);
            assertThat(event.occurredAt()).isNotNull();
            assertThat(event.id()).isNotNull();
        }
    }

    // ==================== Pruebas del método processOverdueCredit ====================

    @Nested
    @DisplayName("Procesamiento individual de crédito en mora")
    class ProcessOverdueCredit {

        @Test
        @DisplayName("Debe procesar un crédito individual correctamente")
        void shouldProcessSingleOverdueCredit() {
            // Arrange
            Credit overdueCredit = Credit.builder()
                    .id("cred-single")
                    .customerId("cust-single")
                    .type(CreditType.PERSONAL)
                    .status(CreditStatus.ACTIVE)
                    .principalAmount(new BigDecimal("10000.00"))
                    .interestRate(new BigDecimal("12.50"))
                    .termMonths(12)
                    .outstandingBalance(new BigDecimal("5000.00"))
                    .nextDueDate(LocalDate.now().minusDays(40))
                    .firstDueDate(LocalDate.now().minusMonths(6))
                    .maturityDate(LocalDate.now().plusMonths(6))
                    .paymentFrequency("MONTHLY")
                    .purpose(null)
                    .schedule(List.of())
                    .build();

            when(persistencePort.save(any(Credit.class))).thenAnswer(invocation ->
                    Single.just(invocation.getArgument(0)));
            when(eventPublisherPort.publish(any())).thenReturn(Completable.complete());
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            overdueDetectionService.processOverdueCredit(overdueCredit)
                    .test().assertNoErrors().assertComplete();

            // Assert
            ArgumentCaptor<Credit> creditCaptor = ArgumentCaptor.forClass(Credit.class);
            verify(persistencePort).save(creditCaptor.capture());
            assertThat(creditCaptor.getValue().getStatus()).isEqualTo(CreditStatus.OVERDUE);
        }
    }
}
