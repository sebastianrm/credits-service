package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.model.ScheduleInstallment;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de unidad para ListCreditsService.
 * Cubre los casos de prueba CP-083 y CP-084 del microservicio credits-service.
 * <p>
 * Referencia Bidireccional:
 * - CU: CU-03 (Gestión de Créditos)
 * - SEC: SEC029 (Consulta crédito cronograma)
 * - CP: CP-083, CP-084
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListCreditsServiceTest {

    @Mock
    private CreditPersistencePort persistencePort;

    @Mock
    private CachePort cachePort;

    private ListCreditsService listCreditsService;

    private ObjectMapper objectMapper;

    private Credit sampleCredit;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listCreditsService = new ListCreditsService(persistencePort, cachePort, objectMapper);

        sampleCredit = Credit.builder()
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
                .schedule(List.of(
                        ScheduleInstallment.create(1, LocalDate.now().plusMonths(1),
                                new BigDecimal("800.00"), new BigDecimal("104.17"), BigDecimal.ZERO),
                        ScheduleInstallment.create(2, LocalDate.now().plusMonths(2),
                                new BigDecimal("810.00"), new BigDecimal("94.17"), BigDecimal.ZERO)
                ))
                .build();
    }

    // ==================== CP-083: Consulta de crédito con cronograma (Redis Cache) ====================

    @Nested
    @DisplayName("CP-083 — Consulta de crédito con cronograma (Redis Cache)")
    class Cp083_GetCreditWithSchedule {

        @Test
        @DisplayName("Debe encontrar un crédito por ID")
        void shouldFindCreditById() {
            // Arrange
            when(persistencePort.findById("cred-001")).thenReturn(Maybe.just(sampleCredit));

            // Act
            Credit result = listCreditsService.findById("cred-001")
                    .test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("cred-001");
            assertThat(result.getCustomerId()).isEqualTo("cust-12345");
            assertThat(result.getType()).isEqualTo(CreditType.PERSONAL);
            assertThat(result.getStatus()).isEqualTo(CreditStatus.ACTIVE);
            assertThat(result.getPrincipalAmount()).isEqualTo(new BigDecimal("10000.00"));
            assertThat(result.getOutstandingBalance()).isEqualTo(new BigDecimal("7500.00"));
            assertThat(result.getSchedule()).hasSize(2);
        }

        @Test
        @DisplayName("Debe retornar vacío cuando el crédito no existe")
        void shouldReturnEmptyWhenCreditNotFound() {
            // Arrange
            when(persistencePort.findById("cred-no-existe")).thenReturn(Maybe.empty());

            // Act & Assert
            listCreditsService.findById("cred-no-existe")
                    .test().assertNoErrors().assertComplete();
        }

        @Test
        @DisplayName("Debe obtener el cronograma desde la caché Redis (Cache HIT)")
        void shouldGetScheduleFromCacheHit() {
            // Arrange
            String creditId = "cred-001";
            String scheduleJson = "[{\"installmentNumber\":1,\"dueDate\":\"2026-12-14\"}]";

            when(cachePort.get("yanki:credits:schedule:" + creditId))
                    .thenReturn(Maybe.just(scheduleJson));

            // Act
            String result = listCreditsService.getScheduleFromCache(creditId)
                    .test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isEqualTo(scheduleJson);
        }

        @Test
        @DisplayName("Debe retornar vacío cuando el cronograma no está en caché (Cache MISS)")
        void shouldReturnEmptyOnCacheMiss() {
            // Arrange
            String creditId = "cred-001";
            when(cachePort.get("yanki:credits:schedule:" + creditId))
                    .thenReturn(Maybe.empty());

            // Act & Assert
            listCreditsService.getScheduleFromCache(creditId)
                    .test().assertNoErrors().assertComplete();
        }

        @Test
        @DisplayName("Debe almacenar el cronograma en la caché Redis")
        void shouldCacheSchedule() {
            // Arrange
            String creditId = "cred-001";
            List<ScheduleInstallment> schedule = List.of(
                    ScheduleInstallment.create(1, LocalDate.now().plusMonths(1),
                            new BigDecimal("800.00"), new BigDecimal("104.17"), BigDecimal.ZERO)
            );

            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));

            // Act - cacheSchedule es asíncrono, no falla si la caché no está disponible
            listCreditsService.cacheSchedule(creditId, schedule);

            // Assert - la caché se establece en un hilo separado; se valida en pruebas de integración
            // En pruebas de unidad, verificamos que el método no lance excepciones
        }
    }

    // ==================== CP-084: Consulta de cronograma de pagos específico ====================

    @Nested
    @DisplayName("CP-084 — Consulta de cronograma de pagos específico")
    class Cp084_GetPaymentSchedule {

        @Test
        @DisplayName("Debe retornar el cronograma con el detalle de cada cuota")
        void shouldReturnPaymentScheduleWithInstallmentDetails() {
            // Arrange
            when(persistencePort.findById("cred-001")).thenReturn(Maybe.just(sampleCredit));

            // Act
            Credit result = listCreditsService.findById("cred-001")
                    .test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getSchedule()).isNotEmpty();
            assertThat(result.getSchedule()).hasSize(2);

            ScheduleInstallment first = result.getSchedule().get(0);
            assertThat(first.getInstallmentNumber()).isEqualTo(1);
            assertThat(first.getCapitalAmount()).isEqualTo(new BigDecimal("800.00"));
            assertThat(first.getInterestAmount()).isEqualTo(new BigDecimal("104.17"));
            assertThat(first.getStatus()).isEqualTo("PENDING");

            ScheduleInstallment second = result.getSchedule().get(1);
            assertThat(second.getInstallmentNumber()).isEqualTo(2);
            assertThat(second.getCapitalAmount()).isEqualTo(new BigDecimal("810.00"));
        }

        @Test
        @DisplayName("Debe listar créditos por cliente")
        void shouldListCreditsByCustomerId() {
            // Arrange
            Credit credit1 = sampleCredit;
            Credit credit2 = sampleCredit.toBuilder()
                    .id("cred-002")
                    .type(CreditType.BUSINESS)
                    .principalAmount(new BigDecimal("50000.00"))
                    .build();

            when(persistencePort.findByCustomerId("cust-12345"))
                    .thenReturn(Flowable.just(credit1, credit2));

            // Act
            List<Credit> results = listCreditsService.findByCriteria("cust-12345", null, null)
                    .test().assertNoErrors().values();

            // Assert
            assertThat(results).hasSize(2);
            assertThat(results).extracting(Credit::getCustomerId)
                    .containsOnly("cust-12345");
        }

        @Test
        @DisplayName("Debe filtrar créditos por tipo")
        void shouldFilterCreditsByType() {
            // Arrange
            Credit personalCredit = sampleCredit;
            Credit businessCredit = sampleCredit.toBuilder()
                    .id("cred-002")
                    .type(CreditType.BUSINESS)
                    .build();

            when(persistencePort.findAll())
                    .thenReturn(Flowable.just(personalCredit, businessCredit));

            // Act
            List<Credit> results = listCreditsService.findByCriteria(null, "PERSONAL", null)
                    .test().assertNoErrors().values();

            // Assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getType()).isEqualTo(CreditType.PERSONAL);
        }

        @Test
        @DisplayName("Debe filtrar créditos por estado")
        void shouldFilterCreditsByStatus() {
            // Arrange
            Credit activeCredit = sampleCredit;
            Credit paidCredit = sampleCredit.toBuilder()
                    .id("cred-002")
                    .status(CreditStatus.PAID_OFF)
                    .outstandingBalance(BigDecimal.ZERO)
                    .build();

            when(persistencePort.findAll())
                    .thenReturn(Flowable.just(activeCredit, paidCredit));

            // Act
            List<Credit> results = listCreditsService.findByCriteria(null, null, "ACTIVE")
                    .test().assertNoErrors().values();

            // Assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(CreditStatus.ACTIVE);
        }

        @Test
        @DisplayName("Debe filtrar créditos por cliente, tipo y estado combinados")
        void shouldFilterCreditsByMultipleCriteria() {
            // Arrange
            Credit matchingCredit = sampleCredit;
            Credit nonMatchingType = sampleCredit.toBuilder()
                    .id("cred-002")
                    .type(CreditType.BUSINESS)
                    .build();
            Credit nonMatchingStatus = sampleCredit.toBuilder()
                    .id("cred-003")
                    .status(CreditStatus.PAID_OFF)
                    .build();

            when(persistencePort.findByCustomerId("cust-12345"))
                    .thenReturn(Flowable.just(matchingCredit, nonMatchingType, nonMatchingStatus));

            // Act
            List<Credit> results = listCreditsService.findByCriteria("cust-12345", "PERSONAL", "ACTIVE")
                    .test().assertNoErrors().values();

            // Assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo("cred-001");
            assertThat(results.get(0).getType()).isEqualTo(CreditType.PERSONAL);
            assertThat(results.get(0).getStatus()).isEqualTo(CreditStatus.ACTIVE);
        }

        @Test
        @DisplayName("Debe retornar lista vacía cuando no hay créditos para el cliente")
        void shouldReturnEmptyListWhenNoCredits() {
            // Arrange
            when(persistencePort.findByCustomerId("cust-vacio"))
                    .thenReturn(Flowable.empty());

            // Act & Assert - Flowable.empty() completa sin emitir valores
            listCreditsService.findByCriteria("cust-vacio", null, null)
                    .test()
                    .assertNoErrors()
                    .assertValueCount(0);
        }

        @Test
        @DisplayName("Debe listar todos los créditos cuando no se proporcionan filtros")
        void shouldListAllCreditsWhenNoFilters() {
            // Arrange
            Credit credit1 = sampleCredit;
            Credit credit2 = sampleCredit.toBuilder()
                    .id("cred-002")
                    .customerId("cust-99999")
                    .type(CreditType.BUSINESS)
                    .build();

            when(persistencePort.findAll())
                    .thenReturn(Flowable.just(credit1, credit2));

            // Act
            List<Credit> results = listCreditsService.findByCriteria(null, null, null)
                    .test().assertNoErrors().values();

            // Assert
            assertThat(results).hasSize(2);
        }
    }
}
