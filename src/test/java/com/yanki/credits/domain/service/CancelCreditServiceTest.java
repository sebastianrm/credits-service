package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
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
 * Pruebas de unidad para CancelCreditService.
 * Cubre la cancelacion de creditos con validacion de estados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CancelCreditServiceTest {

    @Mock
    private CreditPersistencePort persistencePort;

    @Mock
    private CachePort cachePort;

    private CancelCreditService cancelCreditService;

    @BeforeEach
    void setUp() {
        cancelCreditService = new CancelCreditService(persistencePort, cachePort);
    }

    // ==================== Happy Path: Cancelacion exitosa ====================

    @Nested
    @DisplayName("Cancelacion exitosa de credito")
    class CancelSuccess {

        @Test
        @DisplayName("Debe cancelar un credito en estado APPROVED")
        void shouldCancelApprovedCredit() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12);

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));
            when(persistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            Credit result = cancelCreditService.execute(creditId).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(CreditStatus.CANCELLED);
            assertThat(result.getId()).isNotNull();
            verify(persistencePort).save(any(Credit.class));
            verify(cachePort).delete("yanki:credits:schedule:" + creditId);
        }

        @Test
        @DisplayName("Debe cancelar un credito en estado ACTIVE")
        void shouldCancelActiveCredit() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12)
                    .activate();

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));
            when(persistencePort.save(any(Credit.class))).thenAnswer(inv -> Single.just(inv.getArgument(0)));
            when(cachePort.delete(anyString())).thenReturn(Completable.complete());

            // Act
            Credit result = cancelCreditService.execute(creditId).test().assertNoErrors().values().get(0);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(CreditStatus.CANCELLED);
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
            when(persistencePort.findById(creditId)).thenReturn(Maybe.empty());

            // Act & Assert
            assertThatThrownBy(() -> cancelCreditService.execute(creditId).blockingGet())
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

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(paidCredit));

            // Act & Assert
            assertThatThrownBy(() -> cancelCreditService.execute(creditId).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ya pagado totalmente");
        }
    }

    // ==================== Error: Credito ya cancelado ====================

    @Nested
    @DisplayName("Error: Credito ya cancelado")
    class CreditAlreadyCancelled {

        @Test
        @DisplayName("Debe lanzar excepcion si el credito ya esta cancelado")
        void shouldThrowWhenCreditAlreadyCancelled() {
            // Arrange
            String creditId = "credit-123";
            Credit credit = Credit.create("cust-1", CreditType.PERSONAL,
                    new BigDecimal("5000"), new BigDecimal("12"), 12)
                    .cancel();

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));

            // Act & Assert
            assertThatThrownBy(() -> cancelCreditService.execute(creditId).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ya se encuentra cancelado");
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

            when(persistencePort.findById(creditId)).thenReturn(Maybe.just(credit));

            // Act & Assert
            assertThatThrownBy(() -> cancelCreditService.execute(creditId).blockingGet())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("mora");
        }
    }
}
