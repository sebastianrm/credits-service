package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
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
 * Pruebas de unidad para UpdateCreditService.
 * Cubre la actualizacion de tasa de interes y regeneracion de cronograma.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateCreditServiceTest {

    @Mock
    private CreditPersistencePort persistencePort;

    @Mock
    private CachePort cachePort;

    private UpdateCreditService updateCreditService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        updateCreditService = new UpdateCreditService(persistencePort, cachePort, objectMapper);
    }

    // ==================== Happy Path: Actualizacion exitosa ====================

    @Nested
    @DisplayName("Actualizacion exitosa de tasa de interes")
    class UpdateSuccess {

        @Test
        @DisplayName("Debe actualizar la tasa de interes de un credito en estado APPROVED")
        void shouldUpdateInterestRateForApprovedCredit() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12);
            BigDecimal newRate = new BigDecimal("10");

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));
            when(persistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            Credit result = updateCreditService.execute(creditId, newRate).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getInterestRate()).isEqualTo(new BigDecimal("10.00"));
            assertThat(result.getSchedule()).isNotEmpty();
            verify(persistencePort).save(any(Credit.class));
        }

        @Test
        @DisplayName("Debe actualizar la tasa de interes de un credito en estado ACTIVE")
        void shouldUpdateInterestRateForActiveCredit() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12)
                    .activate();
            BigDecimal newRate = new BigDecimal("8.50");

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));
            when(persistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            Credit result = updateCreditService.execute(creditId, newRate).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getInterestRate()).isEqualTo(new BigDecimal("8.50"));
        }

        @Test
        @DisplayName("Debe regenerar el cronograma con la nueva tasa")
        void shouldRegenerateScheduleWithNewRate() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12);
            BigDecimal newRate = new BigDecimal("6");

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));
            when(persistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.set(anyString(), anyString())).thenReturn(Single.fromCallable(() -> null));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            Credit result = updateCreditService.execute(creditId, newRate).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result.getSchedule()).hasSize(12);
            assertThat(result.getInterestRate()).isEqualTo(new BigDecimal("6.00"));
        }
    }

    // ==================== Error: Tasa invalida ====================

    @Nested
    @DisplayName("Error: Tasa de interes invalida")
    class InvalidInterestRate {

        @Test
        @DisplayName("Debe lanzar excepcion si la tasa es nula")
        void shouldThrowWhenRateIsNull() {
            // Arrange
            String creditId = "credit-123";

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, null).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tasa de interés debe ser mayor a cero");
        }

        @Test
        @DisplayName("Debe lanzar excepcion si la tasa es cero")
        void shouldThrowWhenRateIsZero() {
            // Arrange
            String creditId = "credit-123";
            BigDecimal zeroRate = BigDecimal.ZERO;

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, zeroRate).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tasa de interés debe ser mayor a cero");
        }

        @Test
        @DisplayName("Debe lanzar excepcion si la tasa es negativa")
        void shouldThrowWhenRateIsNegative() {
            // Arrange
            String creditId = "credit-123";
            BigDecimal negativeRate = new BigDecimal("-5");

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, negativeRate).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tasa de interés debe ser mayor a cero");
        }

        @Test
        @DisplayName("Debe lanzar excepcion si la tasa excede el 50%")
        void shouldThrowWhenRateExceedsMax() {
            // Arrange
            String creditId = "credit-123";
            BigDecimal highRate = new BigDecimal("51");

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, highRate).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("excede el límite máximo");
        }
    }

    // ==================== Error: Credito no encontrado ====================

    @Nested
    @DisplayName("Error: Credito no encontrado")
    class CreditNotFound {

        @Test
        @DisplayName("Debe lanzar excepcion si el credito no existe")
        void shouldThrowWhenCreditNotFound() {
            // Arrange
            String creditId = "non-existent";
            BigDecimal newRate = new BigDecimal("10");
            when(persistencePort.findById(creditId)).thenReturn(Maybe.empty());

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, newRate).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("no encontrado");
        }
    }

    // ==================== Error: Credito ya pagado ====================

    @Nested
    @DisplayName("Error: Credito ya pagado")
    class CreditAlreadyPaid {

        @Test
        @DisplayName("Debe lanzar excepcion si el credito ya esta pagado")
        void shouldThrowWhenCreditPaidOff() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12);
            Credit paidCredit = credit.applyPayment(new BigDecimal("100000"));
            BigDecimal newRate = new BigDecimal("10");

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(paidCredit));

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, newRate).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ya pagado totalmente");
        }
    }

    // ==================== Error: Credito cancelado ====================

    @Nested
    @DisplayName("Error: Credito cancelado")
    class CreditCancelled {

        @Test
        @DisplayName("Debe lanzar excepcion si el credito esta cancelado")
        void shouldThrowWhenCreditCancelled() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12)
                    .cancel();
            BigDecimal newRate = new BigDecimal("10");

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, newRate).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cancelado");
        }
    }

    // ==================== Error: Credito en mora ====================

    @Nested
    @DisplayName("Error: Credito en mora")
    class CreditOverdue {

        @Test
        @DisplayName("Debe lanzar excepcion si el credito esta en mora")
        void shouldThrowWhenCreditOverdue() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12)
                    .markAsOverdue();
            BigDecimal newRate = new BigDecimal("10");

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));

            // Act & Assert
            assertThatThrownBy(() -> updateCreditService.execute(creditId, newRate).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("mora");
        }
    }
}
