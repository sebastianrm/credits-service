package com.yanki.credits.interfaces.rest.controller;

import com.yanki.credits.domain.model.PaymentSource;
import com.yanki.credits.domain.port.in.CancelCreditUseCase;
import com.yanki.credits.domain.port.in.CreateCreditUseCase;
import com.yanki.credits.domain.port.in.ListCreditsUseCase;
import com.yanki.credits.domain.port.in.RegisterPaymentUseCase;
import com.yanki.credits.domain.port.in.ReversePaymentUseCase;
import com.yanki.credits.domain.port.in.UpdateCreditUseCase;
import com.yanki.credits.domain.port.in.UpdatePaymentUseCase;
import com.yanki.credits.domain.exception.CreditNotFoundException;
import com.yanki.credits.domain.service.ListCreditsService;
import com.yanki.credits.infrastructure.job.OverdueJob;
import com.yanki.credits.interfaces.rest.dto.CreateCreditRequestDto;
import com.yanki.credits.interfaces.rest.dto.CreatePaymentRequestDto;
import com.yanki.credits.interfaces.rest.dto.CreditResponseDto;
import com.yanki.credits.interfaces.rest.dto.ErrorDto;
import com.yanki.credits.interfaces.rest.dto.PaymentResponseDto;
import com.yanki.credits.interfaces.rest.dto.ScheduleInstallmentResponseDto;
import com.yanki.credits.interfaces.rest.dto.UpdateCreditRequestDto;
import com.yanki.credits.interfaces.rest.dto.UpdatePaymentRequestDto;
import com.yanki.credits.interfaces.rest.mapper.CreditRestMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST reactivo para operaciones sobre creditos.
 * Inyecta puertos de entrada del dominio y convierte entre Mono/Flux y RxJava 3.
 *
 * Gestiona el ciclo de vida completo de los creditos: creacion, consulta, pagos,
 * cronogramas y deteccion de mora.
 */
@Slf4j
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
@Tag(name = "Credits", description = "API de gestion de creditos personales y empresariales. Incluye creacion con scoring, consulta con cache Redis, registro de pagos y deteccion de mora.")
public class CreditController {

    private final CreateCreditUseCase createCreditUseCase;
    private final ListCreditsUseCase listCreditsUseCase;
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final UpdateCreditUseCase updateCreditUseCase;
    private final CancelCreditUseCase cancelCreditUseCase;
    private final UpdatePaymentUseCase updatePaymentUseCase;
    private final ReversePaymentUseCase reversePaymentUseCase;
    private final ListCreditsService listCreditsService;
    private final OverdueJob overdueJob;
    private final ObjectMapper objectMapper;

    // ==================== ENDPOINT 1: Crear credito ====================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear un nuevo credito",
            description = "Crea un nuevo credito personal o empresarial validando las reglas de negocio:\n"
                    + "- Verifica que el cliente existe y esta activo\n"
                    + "- Verifica que el cliente no tiene deuda vencida mayor a 30 dias\n"
                    + "- Para creditos personales: valida que no exista un credito activo previo (limite de 1)\n"
                    + "- Ejecuta scoring basico de montos, tasas y plazos\n"
                    + "- Genera automaticamente el cronograma de pagos con sistema frances (cuotas fijas)\n"
                    + "- Publica el evento credit.opened.v1 en Kafka\n"
                    + "- Almacena el cronograma en caché Redis para consultas rapidas"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Credito creado exitosamente con cronograma generado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreditResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud invalida: monto, tasa o plazo fuera de rango",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "Cliente no encontrado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto de negocio: credito personal ya activo o credito ya pagado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "403", description = "Operacion prohibida: cliente con deuda vencida mayor a 30 dias",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<CreditResponseDto> createCredit(
            @Parameter(description = "Datos de la solicitud de credito", required = true)
            @Valid @RequestBody CreateCreditRequestDto request) {
        log.info("[REST] POST /api/credits - customerId={}, type={}, amount={}",
                request.customerId(), request.type(), request.amount());
        return RxJava3Adapter.singleToMono(
                createCreditUseCase.execute(
                        request.customerId(),
                        request.type(),
                        request.amount(),
                        request.interestRate(),
                        request.termMonths())
                        .map(CreditRestMapper::toResponse)
        ).doOnSuccess(dto -> log.info("[REST] POST /api/credits - Crédito creado: id={}", dto.id()))
                .doOnError(e -> log.error("[REST] POST /api/credits - Error: {}", e.getMessage()));
    }

    // ==================== ENDPOINT 2: Listar creditos ====================

    @GetMapping("")
    @Operation(
            summary = "Listar creditos con filtros opcionales",
            description = "Devuelve una lista de creditos filtrados por cliente, tipo y estado.\n"
                    + "Si no se proporcionan filtros, devuelve todos los creditos del sistema.\n"
                    + "Los filtros son combinables entre si."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de creditos obtenida exitosamente (puede ser vacia)",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreditResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Flux<CreditResponseDto> listCredits(
            @Parameter(description = "Filtrar por identificador del cliente titular. Ejemplo: cust-12345")
            @RequestParam(required = false) String customerId,

            @Parameter(description = "Filtrar por tipo de credito. Valores: PERSONAL, BUSINESS, MORTGAGE",
                    example = "PERSONAL")
            @RequestParam(required = false) String type,

            @Parameter(description = "Filtrar por estado del credito. Valores: PENDING, APPROVED, ACTIVE, PAID_OFF, OVERDUE, CANCELLED",
                    example = "ACTIVE")
            @RequestParam(required = false) String status) {
        log.info("[REST] GET /api/credits - customerId={}, type={}, status={}", customerId, type, status);
        return RxJava3Adapter.flowableToFlux(
                listCreditsUseCase.findByCriteria(customerId, type, status)
                        .map(CreditRestMapper::toResponse)
        ).doOnError(e -> log.error("[REST] GET /api/credits - Error: {}", e.getMessage()));
    }

    // ==================== ENDPOINT 3: Consultar credito por ID ====================

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar un credito por identificador",
            description = "Devuelve los detalles completos de un credito por su identificador unico.\n"
                    + "Incluye el cronograma de pagos completo.\n"
                    + "Utiliza caché Redis para optimizar consultas repetidas del cronograma."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credito encontrado con todos sus detalles",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreditResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Credito no encontrado con el identificador proporcionado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<CreditResponseDto> getCreditById(
            @Parameter(description = "Identificador unico del credito a consultar",
                    example = "cred-abc123", required = true)
            @PathVariable String id) {
        log.info("[REST] GET /api/credits/{} - Consultando crédito", id);
        return RxJava3Adapter.maybeToMono(
                listCreditsService.getScheduleFromCache(id)
        ).flatMap(scheduleJson -> {
            return RxJava3Adapter.maybeToMono(
                    listCreditsUseCase.findById(id)
                            .map(credit -> {
                                CreditResponseDto response = CreditRestMapper.toResponse(credit);
                                return response;
                            })
            );
        }).switchIfEmpty(
                RxJava3Adapter.maybeToMono(
                        listCreditsUseCase.findById(id)
                                .map(credit -> {
                                    if (credit.getSchedule() != null && !credit.getSchedule().isEmpty()) {
                                        listCreditsService.cacheSchedule(id, credit.getSchedule());
                                    }
                                    return CreditRestMapper.toResponse(credit);
                                })
                )
        ).switchIfEmpty(
                Mono.error(() -> {
                    log.warn("[REST] GET /api/credits/{} - Crédito no encontrado", id);
                    return new CreditNotFoundException(id);
                })
        );
    }

    // ==================== ENDPOINT 4: Consultar cronograma de pagos ====================

    @GetMapping("/{id}/schedule")
    @Operation(
            summary = "Consultar el cronograma de pagos de un credito",
            description = "Devuelve el cronograma completo de pagos de un credito.\n"
                    + "Cada cuota incluye: numero, fecha de vencimiento, monto de capital, interes, comisiones y total.\n"
                    + "El cronograma se genera con el sistema frances (cuotas fijas mensuales).\n"
                    + "Utiliza caché Redis para optimizar consultas repetidas."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cronograma de pagos obtenido exitosamente (lista vacia si no hay cronograma)",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ScheduleInstallmentResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Credito no encontrado con el identificador proporcionado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor al deserializar el cronograma",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<List<ScheduleInstallmentResponseDto>> getSchedule(
            @Parameter(description = "Identificador unico del credito cuyo cronograma se desea consultar",
                    example = "cred-abc123", required = true)
            @PathVariable String id) {
        log.info("[REST] GET /api/credits/{}/schedule - Consultando cronograma", id);
        return RxJava3Adapter.maybeToMono(
                listCreditsService.getScheduleFromCache(id)
        ).flatMap(scheduleJson -> {
            try {
                List<ScheduleInstallmentResponseDto> schedule = objectMapper.readValue(
                        scheduleJson,
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, ScheduleInstallmentResponseDto.class)
                );
                return Mono.just(schedule);
            } catch (JsonProcessingException e) {
                return Mono.error(new RuntimeException("Error deserializando cronograma", e));
            }
        }).switchIfEmpty(
                RxJava3Adapter.maybeToMono(
                        listCreditsUseCase.findById(id)
                                .map(credit -> {
                                    if (credit.getSchedule() == null || credit.getSchedule().isEmpty()) {
                                        return Collections.<ScheduleInstallmentResponseDto>emptyList();
                                    }
                                    listCreditsService.cacheSchedule(id, credit.getSchedule());
                                    List<ScheduleInstallmentResponseDto> dtoList = credit.getSchedule().stream()
                                            .map(installment -> new ScheduleInstallmentResponseDto(
                                                    installment.getInstallmentNumber(),
                                                    installment.getDueDate(),
                                                    installment.getCapitalAmount(),
                                                    installment.getInterestAmount(),
                                                    installment.getFeeAmount(),
                                                    installment.getTotalAmount(),
                                                    installment.getStatus()
                                            ))
                                            .toList();
                                    return dtoList;
                                })
                )
        ).defaultIfEmpty(Collections.emptyList());
    }

    // ==================== ENDPOINT 5: Registrar pago ====================

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Registrar un pago en un credito",
            description = "Registra un pago en un credito existente aplicando el monto con orden de prioridad:\n"
                    + "1) Mora y comisiones vencidas\n"
                    + "2) Interes vencido\n"
                    + "3) Capital vencido\n"
                    + "4) Interes corriente\n"
                    + "5) Capital corriente\n"
                    + "6) Pago anticipado\n"
                    + "\n"
                    + "Si el saldo llega a cero, el credito cambia automaticamente a estado PAID_OFF.\n"
                    + "Publica el evento credit.payment.registered.v1 en Kafka."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pago registrado exitosamente con distribucion detallada",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Monto de pago invalido (debe ser mayor a cero)",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "Credito no encontrado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto: intento de pagar un credito ya pagado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<PaymentResponseDto> registerPayment(
            @Parameter(description = "Identificador unico del credito al que se aplica el pago",
                    example = "cred-abc123", required = true)
            @PathVariable String id,

            @Parameter(description = "Datos del pago a registrar", required = true)
            @Valid @RequestBody CreatePaymentRequestDto request) {
        PaymentSource source = PaymentSource.OWN_ACCOUNT;
        if (request.source() != null) {
            source = PaymentSource.valueOf(request.source());
        }
        log.info("[REST] POST /api/credits/{}/payments - amount={}, source={}", id, request.amount(), source);
        return RxJava3Adapter.singleToMono(
                registerPaymentUseCase.execute(
                        id,
                        request.amount(),
                        source,
                        request.sourceAccountId(),
                        request.payerCustomerId())
                        .map(CreditRestMapper::toPaymentResponse)
        ).doOnSuccess(dto -> log.info("[REST] POST /api/credits/{}/payments - Pago registrado: id={}", id, dto.id()))
                .doOnError(e -> log.error("[REST] POST /api/credits/{}/payments - Error: {}", id, e.getMessage()));
    }

    // ==================== ENDPOINT 6: Listar pagos de un credito ====================

    @GetMapping("/{id}/payments")
    @Operation(
            summary = "Listar los pagos de un credito",
            description = "Devuelve la lista historica de todos los pagos registrados en un credito.\n"
                    + "Cada pago incluye la distribucion entre capital, interes y comisiones."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pagos obtenida exitosamente (puede ser vacia)",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Flux<PaymentResponseDto> listPayments(
            @Parameter(description = "Identificador unico del credito cuyos pagos se desean consultar",
                    example = "cred-abc123", required = true)
            @PathVariable String id) {
        log.info("[REST] GET /api/credits/{}/payments - Listando pagos", id);
        return RxJava3Adapter.flowableToFlux(
                registerPaymentUseCase.listByCredit(id)
                        .map(CreditRestMapper::toPaymentResponse)
        ).doOnError(e -> log.error("[REST] GET /api/credits/{}/payments - Error: {}", id, e.getMessage()));
    }

    // ==================== ENDPOINT 7: Consultar pago por ID ====================

    @GetMapping("/{id}/payments/{paymentId}")
    @Operation(
            summary = "Consultar un pago especifico por identificador",
            description = "Devuelve los detalles completos de un pago registrado en un credito,\n"
                    + "incluyendo la distribucion del monto entre capital, interes y comisiones."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pago encontrado con todos sus detalles",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Pago o credito no encontrado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<PaymentResponseDto> getPaymentById(
            @Parameter(description = "Identificador unico del credito",
                    example = "cred-abc123", required = true)
            @PathVariable String id,

            @Parameter(description = "Identificador unico del pago a consultar",
                    example = "pay-xyz789", required = true)
            @PathVariable String paymentId) {
        log.info("[REST] GET /api/credits/{}/payments/{} - Consultando pago", id, paymentId);
        return RxJava3Adapter.maybeToMono(
                registerPaymentUseCase.findById(id, paymentId)
                        .map(CreditRestMapper::toPaymentResponse)
        ).doOnError(e -> log.error("[REST] GET /api/credits/{}/payments/{} - Error: {}", id, paymentId, e.getMessage()));
    }

    // ==================== ENDPOINT 8: Ejecutar job de mora ====================

    @PostMapping("/jobs/overdue-detection")
    @Operation(
            summary = "Ejecutar manualmente el job de deteccion de mora",
            description = "Dispara manualmente el job de deteccion de mora que normalmente se ejecuta\n"
                    + "diariamente a las 02:00 AM.\n"
                    + "\n"
                    + "El job:\n"
                    + "- Identifica creditos con cuotas vencidas mayor a 30 dias\n"
                    + "- Cambia el estado de los creditos a OVERDUE\n"
                    + "- Publica el evento credit.overdue.v1 en Kafka para bloqueo transversal\n"
                    + "- Los clientes con mora son bloqueados para nuevos productos en toda la plataforma"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job de deteccion de mora ejecutado exitosamente",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = String.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor al ejecutar el job",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<String> triggerOverdueDetection() {
        log.info("[REST] POST /api/credits/jobs/overdue-detection - Ejecutando job de mora manualmente");
        overdueJob.runManually();
        log.info("[REST] POST /api/credits/jobs/overdue-detection - Job disparado");
        return Mono.just("Job de deteccion de mora ejecutado");
    }

    // ==================== ENDPOINT 9: Actualizar credito (reestructuracion) ====================

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar la tasa de interes de un credito",
            description = "Actualiza la tasa de interes de un credito existente y regenera el cronograma de pagos.\n"
                    + "Se utiliza para reestructuraciones de credito.\n"
                    + "\n"
                    + "Reglas de negocio:\n"
                    + "- Solo se puede actualizar un credito en estado APPROVED o ACTIVE\n"
                    + "- La nueva tasa debe estar entre 0.01% y 50%\n"
                    + "- El cronograma de pagos se regenera automaticamente con el sistema frances\n"
                    + "- La caché Redis del cronograma se invalida y se actualiza"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credito actualizado exitosamente con nuevo cronograma",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreditResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Tasa de interes invalida (fuera de rango 0.01 a 50)",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "Credito no encontrado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto: no se puede actualizar un credito pagado, cancelado o en mora",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<CreditResponseDto> updateCredit(
            @Parameter(description = "Identificador unico del credito a actualizar",
                    example = "cred-abc123", required = true)
            @PathVariable String id,

            @Parameter(description = "Datos de actualizacion del credito", required = true)
            @Valid @RequestBody UpdateCreditRequestDto request) {
        log.info("[REST] PUT /api/credits/{} - newInterestRate={}", id, request.interestRate());
        return RxJava3Adapter.singleToMono(
                updateCreditUseCase.execute(id, request.interestRate())
                        .map(CreditRestMapper::toResponse)
        ).doOnSuccess(dto -> log.info("[REST] PUT /api/credits/{} - Crédito actualizado", id))
                .doOnError(e -> log.error("[REST] PUT /api/credits/{} - Error: {}", id, e.getMessage()));
    }

    // ==================== ENDPOINT 10: Cancelar credito ====================

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Cancelar un credito",
            description = "Cancela un credito cambiando su estado a CANCELLED.\n"
                    + "\n"
                    + "Reglas de negocio:\n"
                    + "- Solo se puede cancelar un credito en estado APPROVED o ACTIVE\n"
                    + "- No se puede cancelar un credito ya pagado (PAID_OFF)\n"
                    + "- No se puede cancelar un credito en mora (OVERDUE) sin regularizar primero\n"
                    + "- La caché Redis del cronograma se invalida"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credito cancelado exitosamente",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreditResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Credito no encontrado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto: no se puede cancelar un credito pagado, ya cancelado o en mora",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<CreditResponseDto> cancelCredit(
            @Parameter(description = "Identificador unico del credito a cancelar",
                    example = "cred-abc123", required = true)
            @PathVariable String id) {
        log.info("[REST] DELETE /api/credits/{} - Cancelando crédito", id);
        return RxJava3Adapter.singleToMono(
                cancelCreditUseCase.execute(id)
                        .map(CreditRestMapper::toResponse)
        ).doOnSuccess(dto -> log.info("[REST] DELETE /api/credits/{} - Crédito cancelado", id))
                .doOnError(e -> log.error("[REST] DELETE /api/credits/{} - Error: {}", id, e.getMessage()));
    }

    // ==================== ENDPOINT 11: Corregir pago ====================

    @PutMapping("/{id}/payments/{paymentId}")
    @Operation(
            summary = "Corregir un pago registrado",
            description = "Corrige el monto de un pago ya registrado cuando se detecta un error en el monto ingresado.\n"
                    + "Ajusta automáticamente el saldo pendiente del crédito según la diferencia entre el monto original y el nuevo monto.\n"
                    + "\n"
                    + "Reglas de negocio:\n"
                    + "- Solo se puede corregir un pago de un crédito en estado APPROVED o ACTIVE\n"
                    + "- No se puede corregir un pago de un crédito cancelado o pagado totalmente\n"
                    + "- No se puede corregir un pago ya revertido\n"
                    + "- El nuevo monto debe ser mayor a cero\n"
                    + "- La caché Redis del cronograma se invalida"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pago corregido exitosamente",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Monto corregido invalido (debe ser mayor a cero)",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "Credito o pago no encontrado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto: no se puede corregir un pago de un credito cancelado, pagado o un pago revertido",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<PaymentResponseDto> updatePayment(
            @Parameter(description = "Identificador unico del credito",
                    example = "cred-abc123", required = true)
            @PathVariable String id,

            @Parameter(description = "Identificador unico del pago a corregir",
                    example = "pay-xyz789", required = true)
            @PathVariable String paymentId,

            @Parameter(description = "Datos de correccion del pago", required = true)
            @Valid @RequestBody UpdatePaymentRequestDto request) {
        log.info("[REST] PUT /api/credits/{}/payments/{} - newAmount={}", id, paymentId, request.amount());
        return RxJava3Adapter.singleToMono(
                updatePaymentUseCase.execute(id, paymentId, request.amount())
                        .map(CreditRestMapper::toPaymentResponse)
        ).doOnSuccess(dto -> log.info("[REST] PUT /api/credits/{}/payments/{} - Pago corregido", id, paymentId))
                .doOnError(e -> log.error("[REST] PUT /api/credits/{}/payments/{} - Error: {}", id, paymentId, e.getMessage()));
    }

    // ==================== ENDPOINT 12: Revertir pago ====================

    @DeleteMapping("/{id}/payments/{paymentId}")
    @Operation(
            summary = "Revertir un pago registrado",
            description = "Revierte un pago ya registrado, devolviendo el monto al saldo pendiente del credito.\n"
                    + "El pago se marca con estado REVERSED y no puede ser corregido ni revertido nuevamente.\n"
                    + "\n"
                    + "Reglas de negocio:\n"
                    + "- Solo se puede revertir un pago de un credito en estado APPROVED, ACTIVE o PAID_OFF\n"
                    + "- Si el credito estaba pagado (PAID_OFF), vuelve a estado ACTIVE\n"
                    + "- No se puede revertir un pago ya revertido\n"
                    + "- La caché Redis del cronograma se invalida"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pago revertido exitosamente",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Credito o pago no encontrado",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto: no se puede revertir un pago de un credito cancelado o un pago ya revertido",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorDto.class)))
    })
    public Mono<PaymentResponseDto> reversePayment(
            @Parameter(description = "Identificador unico del credito",
                    example = "cred-abc123", required = true)
            @PathVariable String id,

            @Parameter(description = "Identificador unico del pago a revertir",
                    example = "pay-xyz789", required = true)
            @PathVariable String paymentId) {
        log.info("[REST] DELETE /api/credits/{}/payments/{} - Revirtiendo pago", id, paymentId);
        return RxJava3Adapter.singleToMono(
                reversePaymentUseCase.execute(id, paymentId)
                        .map(CreditRestMapper::toPaymentResponse)
        ).doOnSuccess(dto -> log.info("[REST] DELETE /api/credits/{}/payments/{} - Pago revertido", id, paymentId))
                .doOnError(e -> log.error("[REST] DELETE /api/credits/{}/payments/{} - Error: {}", id, paymentId, e.getMessage()));
    }
}
