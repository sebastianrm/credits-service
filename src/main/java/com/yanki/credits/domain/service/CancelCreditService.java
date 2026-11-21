package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.port.in.CancelCreditUseCase;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caso de uso encargado de cancelar un crédito existente.
 * Valida que el crédito esté en un estado cancelable (APPROVED o ACTIVE),
 * cambia el estado a CANCELLED, invalida la caché y persiste el cambio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelCreditService implements CancelCreditUseCase {

    private final CreditPersistencePort persistencePort;
    private final CachePort cachePort;

    @Override
    public Single<Credit> execute(String creditId) {
        log.info("Iniciando cancelación de crédito: creditId={}", creditId);

        return persistencePort.findById(creditId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Crédito no encontrado para cancelar: {}", creditId);
                    return new BusinessException(
                            "CREDIT_NOT_FOUND", "Crédito no encontrado: " + creditId);
                }))
                .flatMap(credit -> {
                    log.debug("Crédito encontrado para cancelar: creditId={}, status={}, balance={}",
                            creditId, credit.getStatus(), credit.getOutstandingBalance());
                    // Solo se puede cancelar un crédito en estado APPROVED o ACTIVE
                    if (credit.getStatus() == CreditStatus.PAID_OFF) {
                        log.warn("Cancelación rechazada - crédito ya pagado: creditId={}", creditId);
                        return Single.<Credit>error(new BusinessException(
                                "FORBIDDEN_OPERATION",
                                "No se puede cancelar un crédito ya pagado totalmente"));
                    }
                    if (credit.getStatus() == CreditStatus.CANCELLED) {
                        log.warn("Cancelación rechazada - crédito ya cancelado: creditId={}", creditId);
                        return Single.<Credit>error(new BusinessException(
                                "FORBIDDEN_OPERATION",
                                "El crédito ya se encuentra cancelado"));
                    }
                    if (credit.getStatus() == CreditStatus.OVERDUE) {
                        log.warn("Cancelación rechazada - crédito en mora: creditId={}", creditId);
                        return Single.<Credit>error(new BusinessException(
                                "FORBIDDEN_OPERATION",
                                "No se puede cancelar un crédito en mora. Debe regularizarse primero"));
                    }

                    // Cancelar el crédito
                    Credit cancelledCredit = credit.cancel();
                    log.info("Crédito marcado como CANCELLED: creditId={}", creditId);

                    return persistencePort.save(cancelledCredit)
                            .doOnSuccess(saved -> log.info("Crédito cancelado persistido en MongoDB: creditId={}", creditId))
                            .flatMap(saved -> invalidateCache(creditId)
                                    .toSingleDefault(saved))
                            .doOnSuccess(c -> log.info("✅ Crédito cancelado exitosamente: creditId={}, customerId={}",
                                    creditId, c.getCustomerId()))
                            .doOnError(e -> log.error("Error cancelando crédito {}: {}", creditId, e.getMessage()));
                });
    }

    /**
     * Invalida la caché del cronograma del crédito cancelado.
     *
     * @param creditId identificador del crédito
     * @return completable cuando la caché se invalida
     */
    private Completable invalidateCache(String creditId) {
        String key = "yanki:credits:schedule:" + creditId;
        return cachePort.delete(key)
                .doOnComplete(() -> log.debug("Caché invalidada para crédito cancelado {}", creditId))
                .onErrorComplete();
    }
}
