package com.yanki.credits.infrastructure.persistence.adapter;

import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.port.out.OverdueDetectionPort;
import com.yanki.credits.infrastructure.persistence.mapper.CreditDocumentMapper;
import com.yanki.credits.infrastructure.persistence.repository.SpringDataCreditRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Adaptador de salida que implementa la detección de créditos en mora sobre MongoDB Reactivo.
 * Reutiliza el repositorio y mapper existentes para consultar créditos con cuotas vencidas
 * mayor a 30 días y calcular los días en mora de un crédito individual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueDetectionAdapter implements OverdueDetectionPort {

    private final SpringDataCreditRepository repository;

    @Override
    public Flowable<Credit> findOverdueCredits() {
        LocalDate threshold = LocalDate.now().minusDays(30);
        java.util.List<String> statuses = Arrays.asList("ACTIVE", "OVERDUE");
        log.debug("[OverdueDetection] Buscando créditos en mora: threshold={}, statuses={}", threshold, statuses);
        return RxJava3Adapter.fluxToFlowable(
                repository.findByStatusInAndNextDueDateBefore(statuses, threshold)
                        .map(CreditDocumentMapper::toDomain)
        ).doOnError(e -> log.error("[OverdueDetection] Error buscando créditos en mora: {}", e.getMessage()));
    }

    @Override
    public Single<Integer> countDaysOverdue(Credit credit) {
        if (credit.getNextDueDate() == null) {
            log.debug("[OverdueDetection] Crédito {} sin próxima fecha de vencimiento. Días en mora: 0", credit.getId());
            return Single.just(0);
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(credit.getNextDueDate())) {
            log.debug("[OverdueDetection] Crédito {} no vencido aún: nextDueDate={}, today={}",
                    credit.getId(), credit.getNextDueDate(), today);
            return Single.just(0);
        }
        int days = (int) ChronoUnit.DAYS.between(credit.getNextDueDate(), today);
        log.debug("[OverdueDetection] Crédito {} con {} días en mora: nextDueDate={}, today={}",
                credit.getId(), days, credit.getNextDueDate(), today);
        return Single.just(days);
    }
}
