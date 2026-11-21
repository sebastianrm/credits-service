package com.yanki.credits.domain.service;

import com.yanki.credits.domain.event.CreditOpenedEvent;
import com.yanki.credits.domain.exception.BusinessException;
import com.yanki.credits.domain.model.Credit;
import com.yanki.credits.domain.model.CreditType;
import com.yanki.credits.domain.port.in.CreateCreditUseCase;
import com.yanki.credits.domain.port.out.CachePort;
import com.yanki.credits.domain.port.out.CustomerProfilePort;
import com.yanki.credits.domain.port.out.CustomerProfileView;
import com.yanki.credits.domain.port.out.CustomerRiskPort;
import com.yanki.credits.domain.port.out.CustomerRiskView;
import com.yanki.credits.domain.port.out.CreditEventPublisherPort;
import com.yanki.credits.domain.port.out.CreditPersistencePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caso de uso encargado de crear nuevos créditos.
 * Valida las reglas de negocio: perfil del cliente, deuda vencida,
 * límite de créditos personales, scoring y genera cronograma de pagos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateCreditService implements CreateCreditUseCase {

    private final CreditPersistencePort persistencePort;
    private final CreditEventPublisherPort eventPublisherPort;
    private final CustomerProfilePort customerProfilePort;
    private final CustomerRiskPort customerRiskPort;
    private final CachePort cachePort;
    private final ObjectMapper objectMapper;

    @Override
    public Single<Credit> execute(String customerId, String type,
            BigDecimal principalAmount, BigDecimal interestRate, int termMonths) {
        return execute(customerId, type, principalAmount, interestRate, termMonths, null);
    }

    /**
     * Crea un nuevo crédito validando todas las reglas de negocio.
     * El flujo sigue: 1) Verificar perfil, 2) Verificar riesgo,
     * 3) Validar tipo, 4) Scoring, 5) Crear y persistir, 6) Cachear cronograma, 7) Publicar evento.
     *
     * @param customerId identificador del cliente
     * @param type tipo de crédito
     * @param principalAmount monto principal
     * @param interestRate tasa de interés
     * @param termMonths plazo en meses
     * @param purpose propósito del crédito (opcional)
     * @return crédito creado
     */
    public Single<Credit> execute(String customerId, String type,
            BigDecimal principalAmount, BigDecimal interestRate, int termMonths, String purpose) {

        log.info("Iniciando creación de crédito: customerId={}, type={}, amount={}, rate={}, term={} meses",
                customerId, type, principalAmount, interestRate, termMonths);

        CreditType creditType = CreditType.valueOf(type);

        // 1. Verificar que el cliente existe y está activo
        return customerProfilePort.findByCustomerId(customerId)
                .switchIfEmpty(Single.error(() -> {
                    log.warn("Cliente no encontrado: {}", customerId);
                    return new BusinessException(
                            "CUSTOMER_NOT_FOUND", "Cliente no encontrado: " + customerId);
                }))
                .flatMap(profile -> {
                    log.debug("Perfil del cliente encontrado: customerId={}, type={}, status={}",
                            customerId, profile.customerType(), profile.status());
                    return validateProfile(customerId, profile, creditType);
                })
                // 2. Verificar que no tiene deuda vencida
                .flatMap(validated -> {
                    log.debug("Verificando riesgo para cliente: {}", customerId);
                    return checkRisk(customerId);
                })
                // 3. Validar reglas específicas por tipo
                .flatMap(validated -> {
                    log.debug("Validando reglas por tipo de crédito: {}", creditType);
                    return validateTypeRules(customerId, creditType);
                })
                // 4. Ejecutar scoring básico
                .flatMap(validated -> {
                    log.debug("Ejecutando scoring: amount={}, rate={}, term={}",
                            principalAmount, interestRate, termMonths);
                    return executeScoring(principalAmount, interestRate, termMonths);
                })
                // 5. Crear, persistir y publicar
                .flatMap(validated -> {
                    log.info("Todas las validaciones aprobadas. Creando crédito para cliente: {}", customerId);
                    return createAndSave(customerId, creditType, principalAmount,
                            interestRate, termMonths, purpose);
                })
                .doOnError(e -> log.error("Error creando crédito para cliente {}: {}",
                        customerId, e.getMessage()));
    }

    /**
     * Valida que el perfil del cliente es compatible con el tipo de crédito solicitado.
     */
    private Single<CustomerProfileView> validateProfile(String customerId,
            CustomerProfileView profile, CreditType creditType) {
        log.debug("Validando perfil: customerId={}, customerType={}, creditType={}",
                customerId, profile.customerType(), creditType);

        if (!"ACTIVE".equals(profile.status())) {
            log.warn("Cliente inactivo: customerId={}, status={}", customerId, profile.status());
            return Single.error(new BusinessException(
                    "CUSTOMER_INACTIVE", "El cliente no se encuentra activo"));
        }

        // PERSONAL solo puede solicitar PERSONAL o MORTGAGE
        if ("PERSONAL".equals(profile.customerType())) {
            if (creditType == CreditType.BUSINESS) {
                log.warn("Cliente PERSONAL intentando solicitar crédito EMPRESARIAL: customerId={}", customerId);
                return Single.error(new BusinessException(
                        "INVALID_CREDIT_TYPE",
                        "Los clientes PERSONAL no pueden solicitar créditos EMPRESARIALES"));
            }
        }
        // ENTERPRISE puede solicitar BUSINESS o MORTGAGE
        else if ("ENTERPRISE".equals(profile.customerType())) {
            if (creditType == CreditType.PERSONAL) {
                log.warn("Cliente ENTERPRISE intentando solicitar crédito PERSONAL: customerId={}", customerId);
                return Single.error(new BusinessException(
                        "INVALID_CREDIT_TYPE",
                        "Los clientes ENTERPRISE no pueden solicitar créditos PERSONALES"));
            }
        }

        log.debug("Perfil validado correctamente: customerId={}", customerId);
        return Single.just(profile);
    }

    /**
     * Verifica que el cliente no tenga deuda vencida mayor a 30 días.
     * Regla transversal de bloqueo por deuda vencida.
     */
    private Single<CustomerRiskView> checkRisk(String customerId) {
        CustomerRiskView defaultRisk = new CustomerRiskView(customerId, false, LocalDateTime.now());
        return customerRiskPort.findByCustomerId(customerId)
                .switchIfEmpty(Single.just(defaultRisk))
                .flatMap(risk -> {
                    if (risk.hasOverdueDebt()) {
                        log.warn("Cliente con deuda vencida bloqueado: customerId={}", customerId);
                        return Single.error(new BusinessException(
                                "CUSTOMER_HAS_OVERDUE_DEBT",
                                "El cliente tiene deuda vencida mayor a 30 días. No puede solicitar nuevos productos"));
                    }
                    log.debug("Cliente sin deuda vencida: customerId={}", customerId);
                    return Single.just(risk);
                });
    }

    /**
     * Valida las reglas específicas por tipo de crédito.
     * PERSONAL: máximo 1 crédito activo por cliente.
     * BUSINESS: permite múltiples créditos.
     */
    private Single<Long> validateTypeRules(String customerId, CreditType creditType) {
        if (creditType == CreditType.PERSONAL) {
            return persistencePort.countByCustomerIdAndType(customerId, "PERSONAL")
                    .flatMap(count -> {
                        if (count > 0) {
                            log.warn("Límite de créditos personales excedido: customerId={}, count={}",
                                    customerId, count);
                            return Single.error(new BusinessException(
                                    "PERSONAL_CREDIT_LIMIT_EXCEEDED",
                                    "El cliente ya tiene un crédito personal activo. Máximo 1 permitido"));
                        }
                        log.debug("Cliente sin créditos personales activos: customerId={}", customerId);
                        return Single.just(count);
                    });
        }
        // BUSINESS y MORTGAGE permiten múltiples
        log.debug("Tipo de crédito {} permite múltiples créditos: customerId={}", creditType, customerId);
        return Single.just(0L);
    }

    /**
     * Ejecuta scoring básico del crédito.
     * Valida que los parámetros estén dentro de los límites permitidos.
     */
    private Single<Boolean> executeScoring(BigDecimal principalAmount,
            BigDecimal interestRate, int termMonths) {
        log.debug("Ejecutando scoring: monto={}, tasa={}, plazo={}", principalAmount, interestRate, termMonths);

        // Validar monto mínimo y máximo
        if (principalAmount == null || principalAmount.compareTo(new BigDecimal("100")) < 0) {
            log.warn("Scoring rechazado - monto por debajo del mínimo: amount={}", principalAmount);
            return Single.error(new BusinessException(
                    "SCORING_REJECTED", "El monto del crédito debe ser mayor a S/ 100.00"));
        }
        if (principalAmount.compareTo(new BigDecimal("500000")) > 0) {
            log.warn("Scoring rechazado - monto excede el máximo: amount={}", principalAmount);
            return Single.error(new BusinessException(
                    "SCORING_REJECTED", "El monto excede el límite máximo de S/ 500,000.00"));
        }

        // Validar tasa de interés
        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Scoring rechazado - tasa de interés inválida: rate={}", interestRate);
            return Single.error(new BusinessException(
                    "SCORING_REJECTED", "La tasa de interés debe ser mayor a cero"));
        }
        if (interestRate.compareTo(new BigDecimal("50")) > 0) {
            log.warn("Scoring rechazado - tasa excede el máximo: rate={}", interestRate);
            return Single.error(new BusinessException(
                    "SCORING_REJECTED", "La tasa de interés excede el límite máximo del 50%"));
        }

        // Validar plazo
        if (termMonths < 1 || termMonths > 360) {
            log.warn("Scoring rechazado - plazo fuera de rango: termMonths={}", termMonths);
            return Single.error(new BusinessException(
                    "SCORING_REJECTED", "El plazo debe estar entre 1 y 360 meses"));
        }

        log.info("Scoring aprobado: monto={}, tasa={}, plazo={}", principalAmount, interestRate, termMonths);
        return Single.just(true);
    }

    /**
     * Crea el crédito, lo persiste, cachea el cronograma y publica el evento.
     */
    private Single<Credit> createAndSave(String customerId, CreditType type,
            BigDecimal principalAmount, BigDecimal interestRate, int termMonths, String purpose) {

        Credit credit = Credit.create(customerId, type, principalAmount, interestRate, termMonths, purpose);
        log.info("Entidad de crédito creada en memoria: id={}, customerId={}, type={}, amount={}",
                credit.getId(), customerId, type, principalAmount);

        return persistencePort.save(credit)
                .doOnSuccess(saved -> log.info("Crédito persistido en MongoDB: id={}", saved.getId()))
                .flatMap(saved -> {
                    // Cachear cronograma en Redis
                    return cacheSchedule(saved)
                            .andThen(publishEvent(saved).toSingleDefault(saved));
                })
                .doOnSuccess(c -> log.info("Crédito creado exitosamente: id={}, customerId={}, type={}, amount={}, scheduleSize={}",
                        c.getId(), customerId, c.getType(), c.getPrincipalAmount(), c.getSchedule().size()))
                .doOnError(e -> log.error("Error persistiendo crédito para cliente {}: {}",
                        customerId, e.getMessage()));
    }

    /**
     * Almacena el cronograma de pagos en la caché Redis.
     */
    private io.reactivex.rxjava3.core.Completable cacheSchedule(Credit credit) {
        String key = "yanki:credits:schedule:" + credit.getId();
        try {
            log.debug("Serializando cronograma para caché: creditId={}, installments={}",
                    credit.getId(), credit.getSchedule().size());
            String scheduleJson = objectMapper.writeValueAsString(credit.getSchedule());
            return cachePort.set(key, scheduleJson).ignoreElement()
                    .doOnComplete(() -> log.debug("Cronograma cacheado en Redis: creditId={}, key={}",
                            credit.getId(), key))
                    .onErrorResumeNext(e -> {
                        log.warn("Error cacheando cronograma para crédito {}: {}", credit.getId(), e.getMessage());
                        return io.reactivex.rxjava3.core.Completable.complete();
                    });
        } catch (JsonProcessingException e) {
            log.warn("Error serializando cronograma para crédito {}: {}", credit.getId(), e.getMessage());
            return io.reactivex.rxjava3.core.Completable.complete();
        }
    }

    /**
     * Publica el evento de crédito abierto en Kafka.
     */
    private io.reactivex.rxjava3.core.Completable publishEvent(Credit credit) {
        CreditOpenedEvent event = new CreditOpenedEvent(
                UUID.randomUUID().toString(),
                credit.getId(),
                credit.getCustomerId(),
                credit.getType().name(),
                credit.getPrincipalAmount(),
                LocalDateTime.now()
        );
        log.info("Publicando evento credit.opened.v1: creditId={}, customerId={}",
                credit.getId(), credit.getCustomerId());
        return eventPublisherPort.publish(event)
                .doOnComplete(() -> log.info("Evento credit.opened.v1 publicado: creditId={}", credit.getId()))
                .doOnError(e -> log.error("Error publicando evento credit.opened.v1: creditId={}, error={}",
                        credit.getId(), e.getMessage()));
    }
}
