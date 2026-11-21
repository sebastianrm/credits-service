package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.port.in.UpdateCreditUseCase;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caso de uso encargado de actualizar un crédito existente.
 * Permite la reestructuración del crédito cambiando la tasa de interés
 * y regenerando el cronograma de pagos con el sistema francés.
 * Valida que el crédito esté en un estado modificable (APPROVED o ACTIVE),
 * actualiza la tasa, regenera el cronograma, invalida la caché y persiste el cambio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCreditService implements UpdateCreditUseCase {

    private final CreditPersistencePort persistencePort;
    private final CachePort cachePort;
    private final ObjectMapper objectMapper;

    @Override
    public Single<Credit> execute(String creditId, BigDecimal newInterestRate) {
        log.info("Iniciando actualización de crédito: creditId={}, newInterestRate={}",
                creditId, newInterestRate);

        if (newInterestRate == null || newInterestRate.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Actualización rechazada - tasa inválida: creditId={}, rate={}", creditId, newInterestRate);
            return Single.error(new BusinessException(
                    "INVALID_INTEREST_RATE", "La tasa de interés debe ser mayor a cero"));
        }
        if (newInterestRate.compareTo(new BigDecimal("50")) > 0) {
            log.warn("Actualización rechazada - tasa excede el máximo: creditId={}, rate={}", creditId, newInterestRate);
            return Single.error(new BusinessException(
                    "INVALID_INTEREST_RATE", "La tasa de interés excede el límite máximo del 50%"));
        }

        return persistencePort.findById(creditId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Crédito no encontrado para actualizar: {}", creditId);
                    return new BusinessException(
                            "CREDIT_NOT_FOUND", "Crédito no encontrado: " + creditId);
                }))
                .flatMap(credit -> {
                    log.debug("Crédito encontrado para actualizar: creditId={}, status={}, currentRate={}",
                            creditId, credit.getStatus(), credit.getInterestRate());
                    // Solo se puede actualizar un crédito en estado APPROVED o ACTIVE
                    if (credit.getStatus() == CreditStatus.PAID_OFF) {
                        log.warn("Actualización rechazada - crédito ya pagado: creditId={}", creditId);
                        return Single.<Credit>error(new BusinessException(
                                "FORBIDDEN_OPERATION",
                                "No se puede actualizar un crédito ya pagado totalmente"));
                    }
                    if (credit.getStatus() == CreditStatus.CANCELLED) {
                        log.warn("Actualización rechazada - crédito cancelado: creditId={}", creditId);
                        return Single.<Credit>error(new BusinessException(
                                "FORBIDDEN_OPERATION",
                                "No se puede actualizar un crédito cancelado"));
                    }
                    if (credit.getStatus() == CreditStatus.OVERDUE) {
                        log.warn("Actualización rechazada - crédito en mora: creditId={}", creditId);
                        return Single.<Credit>error(new BusinessException(
                                "FORBIDDEN_OPERATION",
                                "No se puede actualizar un crédito en mora. Debe regularizarse primero"));
                    }

                    // Actualizar la tasa de interés y regenerar el cronograma
                    Credit updatedCredit = credit.updateInterestRate(newInterestRate);
                    log.info("Tasa de interés actualizada: creditId={}, oldRate={}, newRate={}, newScheduleSize={}",
                            creditId, credit.getInterestRate(), newInterestRate, updatedCredit.getSchedule().size());

                    return persistencePort.save(updatedCredit)
                            .doOnSuccess(saved -> log.info("Crédito actualizado persistido en MongoDB: creditId={}", creditId))
                            .flatMap(saved -> cacheSchedule(saved)
                                    .andThen(invalidateOldCache(creditId))
                                    .toSingleDefault(saved))
                            .doOnSuccess(c -> log.info("✅ Crédito actualizado exitosamente: creditId={}, newRate={}",
                                    creditId, newInterestRate))
                            .doOnError(e -> log.error("Error actualizando crédito {}: {}", creditId, e.getMessage()));
                });
    }

    /**
     * Almacena el nuevo cronograma de pagos en la caché Redis.
     *
     * @param credit crédito actualizado
     * @return completable cuando la caché se actualiza
     */
    private Completable cacheSchedule(Credit credit) {
        String key = "yanki:credits:schedule:" + credit.getId();
        try {
            String scheduleJson = objectMapper.writeValueAsString(credit.getSchedule());
            return cachePort.set(key, scheduleJson).ignoreElement()
                    .doOnComplete(() -> log.debug("Cronograma actualizado en caché para crédito {}", credit.getId()))
                    .onErrorResumeNext(e -> {
                        log.warn("Error cacheando cronograma para crédito {}: {}", credit.getId(), e.getMessage());
                        return Completable.complete();
                    });
        } catch (JsonProcessingException e) {
            log.warn("Error serializando cronograma para crédito {}: {}", credit.getId(), e.getMessage());
            return Completable.complete();
        }
    }

    /**
     * Invalida la caché anterior del cronograma del crédito.
     *
     * @param creditId identificador del crédito
     * @return completable cuando la caché se invalida
     */
    private Completable invalidateOldCache(String creditId) {
        String key = "yanki:credits:schedule:" + creditId;
        return cachePort.delete(key)
                .doOnComplete(() -> log.debug("Caché anterior invalidada para crédito {}", creditId))
                .onErrorComplete();
    }
}
