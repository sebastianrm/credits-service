package com.yanki.credits.infrastructure.job;

import com.yanki.credits.domain.service.OverdueDetectionService;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado que se ejecuta diariamente a las 02:00 UTC-5.
 * Detecta créditos con mora mayor a 30 días y publica eventos de mora
 * que activan el bloqueo transversal en otros microservicios.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueJob {

    private final CreditPersistencePort persistencePort;
    private final OverdueDetectionService overdueDetectionService;

    /**
     * Ejecuta la detección diaria de créditos en mora.
     * Programado para ejecutarse a las 02:00 AM cada día.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runOverdueDetection() {
        log.info("[Job] ========================================");
        log.info("[Job] Job de detección de mora iniciado (programado 02:00 AM)");
        log.info("[Job] ========================================");

        try {
            persistencePort.findOverdueCredits()
                    .flatMapCompletable(overdueDetectionService::processOverdueCredit)
                    .subscribe(
                            () -> {
                                log.info("[Job] ========================================");
                                log.info("[Job] Job de detección de mora completado exitosamente");
                                log.info("[Job] ========================================");
                            },
                            error -> log.error("[Job] Error crítico en job de detección de mora: {}",
                                    error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("[Job] Excepción lanzada en job de detección de mora: {}", e.getMessage(), e);
        }
    }

    /**
     * Ejecución manual del job de detección de mora.
     * Se usa para pruebas o ejecución on-demand.
     */
    public void runManually() {
        log.info("[Job] Job de detección de mora ejecutado manualmente (trigger REST)");
        try {
            runOverdueDetection();
        } catch (Exception e) {
            log.error("[Job] Error ejecutando job de mora manualmente: {}", e.getMessage(), e);
        }
    }
}
