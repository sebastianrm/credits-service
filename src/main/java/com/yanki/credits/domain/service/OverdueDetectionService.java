package com.yanki.credits.domain.service;

import com.yanki.credits.domain.event.CreditOverdueEvent;
import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditEventPublisherPort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.yanki.credits.domain.port.out.OverdueDetectionPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio de dominio encargado de detectar créditos en mora mayor a 30 días.
 * Se ejecuta como job diario a las 02:00 UTC-5 y publica eventos de mora
 * que activan el bloqueo transversal en otros microservicios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueDetectionService {

    private final CreditPersistencePort persistencePort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final CachePort cachePort;
    private final OverdueDetectionPort overdueDetectionPort;

    /**
     * Ejecuta la detección diaria de créditos en mora.
     * Para cada crédito con cuotas vencidas mayor a 30 días:
     * 1) Actualiza el estado a OVERDUE
     * 2) Publica el evento yanki.credit.overdue.v1
     * 3) Invalida la caché del cronograma
     */
    public Completable executeOverdueDetection() {
        log.info("========================================");
        log.info("Iniciando job de detección de mora...");
        log.info("========================================");

        return persistencePort.findOverdueCredits()
                .doOnNext(c -> log.debug("Crédito candidato a mora: creditId={}, customerId={}, status={}",
                        c.getId(), c.getCustomerId(), c.getStatus()))
                .flatMapCompletable(this::processOverdueCredit)
                .doOnComplete(() -> {
                    log.info("========================================");
                    log.info("Job de detección de mora finalizado exitosamente.");
                    log.info("========================================");
                })
                .doOnError(e -> log.error("Error crítico en job de detección de mora: {}", e.getMessage()));
    }

    /**
     * Procesa un crédito individual detectado en mora.
     * Marca el crédito como OVERDUE, publica el evento y invalida caché.
     *
     * @param credit crédito en mora
     * @return completable cuando el procesamiento finaliza
     */
    public Completable processOverdueCredit(Credit credit) {
        log.debug("Procesando crédito para detección de mora: creditId={}, customerId={}, status={}",
                credit.getId(), credit.getCustomerId(), credit.getStatus());

        if (credit.getStatus() == CreditStatus.OVERDUE) {
            log.debug("Crédito {} ya está en mora. Saltando...", credit.getId());
            return Completable.complete();
        }

        // Calcular días en mora
        int daysOverdue = calculateDaysOverdue(credit);
        if (daysOverdue <= 30) {
            log.debug("Crédito {} tiene {} días en mora. Por debajo del umbral de 30 días. Saltando...",
                    credit.getId(), daysOverdue);
            return Completable.complete();
        }

        log.warn("⚠️ Crédito en mora detectado: creditId={}, customerId={}, daysOverdue={}, balance={}",
                credit.getId(), credit.getCustomerId(), daysOverdue, credit.getOutstandingBalance());

        // Marcar como OVERDUE
        Credit overdueCredit = credit.markAsOverdue();
        log.info("Crédito marcado como OVERDUE: creditId={}", credit.getId());

        return persistencePort.save(overdueCredit)
                .doOnSuccess(saved -> log.info("Crédito actualizado a OVERDUE en MongoDB: creditId={}", credit.getId()))
                .ignoreElement()
                .andThen(publishOverdueEvent(credit, daysOverdue))
                .andThen(invalidateCache(credit.getId()))
                .doOnComplete(() -> log.info("✅ Crédito {} procesado completamente como mora ({} días)",
                        credit.getId(), daysOverdue))
                .onErrorResumeNext(e -> {
                    log.error("❌ Error procesando crédito en mora {}: {}", credit.getId(), e.getMessage());
                    return Completable.complete();
                });
    }

    /**
     * Calcula los días en mora de un crédito basado en la próxima fecha de vencimiento.
     *
     * @param credit crédito a evaluar
     * @return número de días en mora
     */
    private int calculateDaysOverdue(Credit credit) {
        if (credit.getNextDueDate() == null) {
            log.debug("Crédito {} sin próxima fecha de vencimiento. Días en mora: 0", credit.getId());
            return 0;
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(credit.getNextDueDate())) {
            log.debug("Crédito {} no vencido aún: nextDueDate={}, today={}",
                    credit.getId(), credit.getNextDueDate(), today);
            return 0;
        }
        int days = (int) ChronoUnit.DAYS.between(credit.getNextDueDate(), today);
        log.debug("Crédito {} con {} días en mora: nextDueDate={}, today={}",
                credit.getId(), days, credit.getNextDueDate(), today);
        return days;
    }

    /**
     * Publica el evento de mora en Kafka.
     *
     * @param credit crédito en mora
     * @param daysOverdue días en mora
     * @return completable cuando el evento se publica
     */
    private Completable publishOverdueEvent(Credit credit, int daysOverdue) {
        CreditOverdueEvent event = new CreditOverdueEvent(
                UUID.randomUUID().toString(),
                credit.getId(),
                credit.getCustomerId(),
                daysOverdue,
                credit.getOutstandingBalance(),
                LocalDateTime.now()
        );
        return eventPublisherPort.publish(event)
                .doOnComplete(() -> log.info("Evento de mora publicado para crédito {}", credit.getId()));
    }

    /**
     * Invalida la caché del cronograma de un crédito.
     *
     * @param creditId identificador del crédito
     * @return completable cuando la caché se invalida
     */
    private Completable invalidateCache(String creditId) {
        String key = "yanki:credits:schedule:" + creditId;
        return cachePort.delete(key)
                .doOnComplete(() -> log.debug("Caché invalidada para crédito {}", creditId))
                .onErrorComplete();
    }
}
