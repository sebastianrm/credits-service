package com.yanki.credits.domain.service;

import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditStatus;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.port.in.ListCreditsUseCase;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caso de uso encargado de consultar créditos por criterios.
 * Utiliza caché Redis para optimizar las consultas de cronograma.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListCreditsService implements ListCreditsUseCase {

    private final CreditPersistencePort persistencePort;
    private final CachePort cachePort;
    private final ObjectMapper objectMapper;

    @Override
    public Maybe<Credit> findById(String id) {
        log.debug("Buscando crédito por ID: {}", id);
        return persistencePort.findById(id)
                .doOnSuccess(c -> log.debug("Crédito encontrado: id={}, customerId={}, status={}",
                        c.getId(), c.getCustomerId(), c.getStatus()))
                .doOnError(e -> log.error("Error buscando crédito por ID {}: {}", id, e.getMessage()));
    }

    @Override
    public Flowable<Credit> findByCriteria(String customerId, String type, String status) {
        log.info("Buscando créditos con criterios: customerId={}, type={}, status={}",
                customerId, type, status);
        Flowable<Credit> credits;
        if (customerId != null) {
            log.debug("Filtrando por customerId: {}", customerId);
            credits = persistencePort.findByCustomerId(customerId);
        } else {
            log.debug("Listando todos los créditos");
            credits = persistencePort.findAll();
        }
        if (type != null) {
            CreditType creditType = CreditType.valueOf(type);
            log.debug("Filtrando por tipo: {}", creditType);
            credits = credits.filter(c -> c.getType() == creditType);
        }
        if (status != null) {
            CreditStatus creditStatus = CreditStatus.valueOf(status);
            log.debug("Filtrando por estado: {}", creditStatus);
            credits = credits.filter(c -> c.getStatus() == creditStatus);
        }
        return credits.doOnNext(c -> log.debug("Crédito en resultado: id={}, type={}, status={}",
                c.getId(), c.getType(), c.getStatus()));
    }

    /**
     * Consulta el cronograma de pagos de un crédito desde la caché Redis.
     * Si no está en caché, retorna vacío indicando que se debe reconstruir desde MongoDB.
     *
     * @param creditId identificador del crédito
     * @return cronograma en formato JSON o vacío
     */
    public Maybe<String> getScheduleFromCache(String creditId) {
        String key = "yanki:credits:schedule:" + creditId;
        log.debug("Consultando cronograma desde caché Redis: creditId={}, key={}", creditId, key);
        return cachePort.get(key)
                .doOnSuccess(schedule -> {
                    if (schedule != null) {
                        log.info("Cache HIT - Cronograma obtenido desde Redis: creditId={}", creditId);
                    } else {
                        log.debug("Cache MISS - Cronograma no encontrado en Redis: creditId={}", creditId);
                    }
                })
                .doOnError(e -> log.error("Error consultando caché para crédito {}: {}", creditId, e.getMessage()));
    }

    /**
     * Almacena el cronograma de un crédito en la caché Redis.
     * Se usa cuando hay cache MISS y se reconstruye el cronograma desde MongoDB.
     *
     * @param creditId identificador del crédito
     * @param schedule cronograma a cachear
     */
    public void cacheSchedule(String creditId, java.util.List<com.yanki.credits.domain.model.ScheduleInstallment> schedule) {
        String key = "yanki:credits:schedule:" + creditId;
        log.debug("Almacenando cronograma en caché: creditId={}, installments={}", creditId, schedule.size());
        try {
            String scheduleJson = objectMapper.writeValueAsString(schedule);
            cachePort.set(key, scheduleJson).subscribe(
                    v -> log.info("Cronograma cacheado en Redis: creditId={}, installments={}", creditId, schedule.size()),
                    e -> log.error("Error cacheando cronograma para crédito {}: {}", creditId, e.getMessage())
            );
        } catch (JsonProcessingException e) {
            log.error("Error serializando cronograma para crédito {}: {}", creditId, e.getMessage());
        }
    }
}
