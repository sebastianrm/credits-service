# AGENTS.md — credits-service

## Identidad del Agente

- **Nombre:** `yanki-credits-agent`
- **Versión:** 2.3.0
- **Servicio:** `credits-service`
- **Puerto:** 8084
- **Parte:** I / III (Core Banking + Event-Driven)
- **Estado:** 🟢 Hexagonal implementado — 100% completado
- **Última actualización:** 2026-11-22

---

## Rol del Agente

Este agente actúa como **ingeniero en sistemas experto** en el dominio de créditos de la plataforma bancaria Yanki, y su **rol principal es construir, completar, corregir y evolucionar el microservicio `credits-service`** usando la documentación disponible del proyecto como fuente de verdad técnica y funcional.

Debe poder trabajar de manera **autónoma**, tomando como insumos consolidados:
- `credits-service/README.md`
- `credits-service/AGENTS.md` (este archivo)
- `README.md` de la raíz del monorepo
- `../docs/CASOS_USO`
- `../docs/CASOS_USO`
- `../docs/CASOS_PRUEBA` (CP-075 a CP-084)
- `../docs/CASOS_PRUEBA` (Sección 3)
- `docs/EVENTOS.md`
- `CONVENTIONS.md`
- `system-prompt-auditoria-hexagonal-yanki.md` (obligatorio para auditoría de código)

Si existe conflicto entre documentos, debe priorizar:
1. `system-prompt-auditoria-hexagonal-yanki.md` (reglas de arquitectura)
2. `AGENTS.md` (raíz del monorepo — Reglas de Oro)
3. `CONVENTIONS.md`
4. `docs/EVENTOS.md`
5. `README.md` raíz
6. `credits-service/README.md`
7. `../docs/CASOS_USO`
8. `../docs/CASOS_PRUEBA`

### Alcance funcional que debe dominar

- Manejo completo del ciclo de vida de créditos: apertura, evaluación, activación, pagos, amortización, consulta y eventual reestructuración.
- Detección y gestión de mora: identificación de créditos vencidos, actualización de estado y publicación del evento `credit.overdue.v1` para bloqueo transversal.
- Actualización de vistas locales para soporte de validaciones, scoring, límites y decisiones de negocio.
- Participación como productor y consumidor clave en la arquitectura orientada a eventos Kafka.

### Conocimiento funcional obligatorio

El agente debe ser experto en los conceptos funcionales propios de este microservicio:
- Diferencias entre crédito personal, empresarial e hipotecario
- Límite de un solo crédito personal por cliente cuando aplique la regla
- Pagos parciales, pagos anticipados y pagos de terceros
- Cálculo de amortización, saldo pendiente, intereses y vencimientos
- Manejo de mora y su impacto transversal sobre otros microservicios
- Validaciones por perfil de cliente, estado del cliente y consistencia de vistas locales

### Casos de borde y errores que debe contemplar

El agente debe diseñar e implementar considerando explícitamente:
- Apertura rechazada por límite de crédito personal
- Pagos con monto inválido, cero o negativo
- Pagos sobre créditos inexistentes, cancelados o totalmente pagados
- Pagos duplicados por reintentos o reprocesamiento de eventos
- Eventos Kafka duplicados o fuera de orden
- Inconsistencia temporal entre vistas locales y estado real del dominio
- Clientes bloqueados, con deuda vencida o con información incompleta
- Errores de conectividad con MongoDB, Redis, Kafka, Config Server o Eureka
- Reconstrucción de vistas locales e idempotencia de consumidores
- Rechazo de crédito por tipo de cliente incompatible
- Reestructuración no permitida por estado del crédito
- Rechazo de cronograma o consulta por falta de ownership/autorización
- Conciliación de pagos iniciados por débito y confirmados por `yanki.debit.payment.settled.v1`

---

## Capacidades Técnicas y de Análisis

| Tecnología | Descripción |
|------------|-------------|
| Lenguaje | Java 17, Spring Boot 3.3.5, Spring Cloud 2023.0.3 |
| Programación Reactiva | Project Reactor, RxJava 3.1.10 para lógica de servicios y WebFlux |
| Datos | Spring Data Reactive con MongoDB para acceso a base propia `yanki_credits` |
| Caché | Redis reactivo usado para cálculos de cuotas y planes de amortización con TTL configurable |
| Mensajería | Spring Kafka para producción y consumo de eventos de dominio (3 tópicos outbound, 4 inbound consumidos) |
| Scheduling | Job reactivo diario para detectar créditos en mora (cron 02:00 UTC-5) |
| Calidad y Testing | JaCoCo, 82 tests unitarios con Mockito (CP-075 a CP-084 + CRUD completo de Credit y Payment) |

---

## 🏛️ Arquitectura Hexagonal — Estado Actual y Obligaciones

### Estado de Arquitectura Hexagonal (2026-11-14)

El credits-service **cumple** con la Arquitectura Hexagonal requerida por las Reglas de Oro de Yanki. La refactorización hexagonal se completó exitosamente.

#### Cumplimiento Hexagonal

| Criterio | Estado | Detalle |
|----------|--------|---------|
| Paquetes `domain`, `infrastructure`, `interfaces` | ✅ Cumple | Estructura hexagonal completa |
| Entidades sin `@Document` en `domain.model` | ✅ Cumple | `@Document` solo en `infrastructure.persistence.document` |
| Puertos de entrada (`domain.port.in`) | ✅ Cumple | 7 puertos: CreateCredit, ListCredits, RegisterPayment, UpdateCredit, CancelCredit, UpdatePayment, ReversePayment |
| Puertos de salida (`domain.port.out`) | ✅ Cumple | 9 archivos: 7 puertos (Persistence, EventPublisher, Cache, Profile, Risk, Overdue) + 2 interfaces de vista (CustomerProfileView, CustomerRiskView) |
| Adaptadores de infraestructura | ✅ Cumple | MongoDB, Redis, Kafka, Job, OpenAPI |
| Controlador inyecta puertos de entrada | ✅ Cumple | `CreditController` inyecta `*UseCase` interfaces |
| RxJava 3 en dominio | ✅ Cumple | `Single`, `Maybe`, `Flowable`, `Completable` de `io.reactivex.rxjava3.core` |
| `BigDecimal` para montos | ✅ Cumple | `principalAmount`, `interestRate`, `outstandingBalance` usan `BigDecimal` |
| `java.time` para fechas | ✅ Cumple | `LocalDate`, `LocalDateTime` en lugar de `Date` |
| Sin `.block()` / `.blockingGet()` | ✅ Cumple | Sin bloqueos reactivos detectados |
| Sin imports de Spring Data/Kafka/Web en domain | ✅ Cumple | Dominio 100% puro |

#### Observaciones No Bloqueantes

| Observación | Detalle |
|-------------|---------|
| **jackson-datatype-jsr310** | Warnings al serializar `LocalDate` en caché Redis — la funcionalidad opera correctamente con fallback |

### Estructura Actual (Hexagonal — Implementada)

```
com.yanki.credits
├── domain
│   ├── model/Credit.java, Payment.java, ScheduleInstallment.java, enums (SIN @Document)
│   ├── port/in/CreateCreditUseCase.java, ListCreditsUseCase.java, RegisterPaymentUseCase.java,
│   │              UpdateCreditUseCase.java, CancelCreditUseCase.java,
│   │              UpdatePaymentUseCase.java, ReversePaymentUseCase.java
│   ├── port/out/CreditPersistencePort.java, PaymentPersistencePort.java,
│   │              CreditEventPublisherPort.java, CachePort.java,
│   │              CustomerProfilePort.java, CustomerRiskPort.java, OverdueDetectionPort.java
│   ├── service/CreateCreditService.java, ListCreditsService.java, RegisterPaymentService.java,
│   │              UpdateCreditService.java, CancelCreditService.java,
│   │              UpdatePaymentService.java, ReversePaymentService.java,
│   │              OverdueDetectionService.java
│   ├── event/CreditEvent.java, CreditOpenedEvent.java, CreditPaymentRegisteredEvent.java, CreditOverdueEvent.java,
│   │              CustomerRegisteredEvent.java, CustomerUpdatedEvent.java, CustomerDeletedEvent.java
│   └── exception/BusinessException.java, CreditNotFoundException.java
├── infrastructure
│   ├── persistence/document/CreditDocument.java, PaymentDocument.java, ScheduleInstallmentDocument.java,
│   │                     CustomerProfileViewDocument.java, CustomerRiskViewDocument.java
│   ├── persistence/repository/SpringDataCreditRepository.java, SpringDataPaymentRepository.java,
│   │                     SpringDataCustomerProfileRepository.java, SpringDataCustomerRiskRepository.java
│   ├── persistence/mapper/CreditDocumentMapper.java, PaymentDocumentMapper.java, ScheduleInstallmentDocumentMapper.java
│   ├── persistence/adapter/CreditMongoAdapter.java, PaymentMongoAdapter.java,
│   │                     CustomerProfileAdapter.java, CustomerRiskAdapter.java, OverdueDetectionAdapter.java
│   ├── cache/adapter/CacheRedisAdapter.java
│   ├── messaging/adapter/CreditKafkaPublisherAdapter.java
│   ├── messaging/consumer/CustomerProfileConsumer.java, CustomerRiskConsumer.java
│   ├── job/OverdueJob.java
│   └── config/KafkaConsumerConfig.java
└── interfaces/rest
    ├── controller/CreditController.java
    ├── dto/CreateCreditRequestDto.java, CreatePaymentRequestDto.java, CreditResponseDto.java,
    │              PaymentResponseDto.java, ScheduleInstallmentResponseDto.java,
    │              UpdateCreditRequestDto.java, UpdatePaymentRequestDto.java, ErrorDto.java
    ├── mapper/CreditRestMapper.java
    ├── config/OpenApiConfig.java
    └── GlobalExceptionHandler.java
```

### Referencia Obligatoria

Ante cualquier tarea de auditoría, refactorización o implementación de arquitectura hexagonal, consultar SIEMPRE:
[`../../system-prompt-auditoria-hexagonal-yanki.md`](../../system-prompt-auditoria-hexagonal-yanki.md)

---

## Protocolos de Comunicación

### APIs REST (Inbound)

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/credits` | Solicita un nuevo crédito |
| GET | `/api/credits` | Lista créditos filtrados por cliente, tipo y estado |
| GET | `/api/credits/{id}` | Detalle del crédito con plan de pagos |
| PUT | `/api/credits/{id}` | Actualiza la tasa de interés y regenera el cronograma |
| DELETE | `/api/credits/{id}` | Cancela el crédito (cambia estado a CANCELLED) |
| GET | `/api/credits/{id}/schedule` | Consulta el plan de amortización |
| POST | `/api/credits/{id}/payments` | Registrar pago para un crédito |
| GET | `/api/credits/{id}/payments` | Consultar historial de pagos |
| GET | `/api/credits/{id}/payments/{paymentId}` | Consultar detalle de pago |
| PUT | `/api/credits/{id}/payments/{paymentId}` | Corregir monto de un pago registrado |
| DELETE | `/api/credits/{id}/payments/{paymentId}` | Revertir un pago registrado |
| POST | `/api/credits/jobs/overdue-detection` | Ejecuta manualmente el job de mora |

### Cobertura HTTP mínima por endpoint

- `POST /api/credits`: `201`, `202`, `400`, `401`, `403`, `404`, `409`, `422`, `500`, `503`
- `GET /api/credits`: `200`, `400`, `401`, `403`, `500`, `503`
- `GET /api/credits/{id}`: `200`, `400`, `401`, `403`, `404`, `500`, `503`
- `PUT /api/credits/{id}`: `200`, `400`, `401`, `403`, `404`, `500`, `503`
- `DELETE /api/credits/{id}`: `200`, `401`, `403`, `404`, `500`, `503`
- `GET /api/credits/{id}/schedule`: `200`, `400`, `401`, `403`, `404`, `500`, `503`
- `POST /api/credits/{id}/payments`: `200`, `201`, `400`, `401`, `403`, `404`, `409`, `422`, `500`, `503`
- `GET /api/credits/{id}/payments`: `200`, `404`, `500`, `503`
- `GET /api/credits/{id}/payments/{paymentId}`: `200`, `404`, `500`, `503`
- `PUT /api/credits/{id}/payments/{paymentId}`: `200`, `400`, `404`, `409`, `500`, `503`
- `DELETE /api/credits/{id}/payments/{paymentId}`: `200`, `404`, `409`, `500`, `503`
- `POST /api/credits/jobs/overdue-detection`: `200`, `500`, `503`

### Kafka (Outbound)

| Topic | Clave | Descripción |
|-------|-------|-------------|
| `yanki.credit.opened.v1` | `creditId` | Indica crédito abierto exitosamente |
| `yanki.credit.payment.registered.v1` | `creditId` | Pago registrado en un crédito |
| `yanki.credit.overdue.v1` | `customerId` | Mora detectada y bloqueo transversal |

### Kafka (Inbound)

| Topic | Propósito | Vista Local |
|-------|-----------|-------------|
| `yanki.customer.registered.v1` | Registro de nuevo cliente | `customer_profile_view` |
| `yanki.customer.updated.v1` | Actualización de perfil/tipo | `customer_profile_view` |
| `yanki.customer.deleted.v1` | Bloqueo o baja lógica del cliente | `customer_profile_view` |
| `yanki.credit.overdue.v1` | Mora detectada (autoconsumo) | `customer_risk_view` |

---

## Ciclo de Vida y Deployment

- El agente inicia configurando conexión con Config Server, Eureka, MongoDB, Redis y Kafka.
- Reconstruye vistas locales desde topics Kafka compactados.
- Ejecuta job diario para detección de mora.
- Expone HTTP server en puerto 8084 con API REST documentada.

---

## Cobertura QA mínima obligatoria

El agente debe asegurar cobertura funcional sobre los casos QA relevantes para este servicio (CP-075 a CP-084):

| CP | Tipo | Descripción |
|----|------|-------------|
| CP-075 | Happy Path | Apertura de crédito personal exitosa |
| CP-076 | Error | Apertura crédito personal rechazada: ya tiene crédito personal |
| CP-077 | Error | Apertura crédito personal rechazada: cliente con deuda vencida |
| CP-078 | Happy Path | Apertura crédito empresarial exitosa (múltiples permitidos) |
| CP-079 | Happy Path | Pago de crédito exitoso desde cuenta propia (event-driven) |
| CP-080 | Error | Pago de crédito rechazado: fondos insuficientes en cuenta origen |
| CP-081 | Happy Path | Pago de crédito efectivo/terceros (sin débito cuenta) |
| CP-082 | Happy Path (Job) | Job diario mora >30 días: detección y bloqueo transversal |
| CP-083 | Happy Path | Consulta de crédito con cronograma (Redis Cache) |
| CP-084 | Happy Path | Consulta de cronograma de pagos específico |

Adicionalmente, debe diseñar mocks para:
- Kafka producer/consumer
- Redis
- Mongo reactive repositories
- Confirmaciones asíncronas de débito
- Vistas locales derivadas de eventos

---

## Manejo de Errores

| Código Error | HTTP | Sinonimia |
|--------------|-------|-----------|
| `CREDIT_NOT_FOUND` | 404 | Crédito inexistente |
| `PERSONAL_CREDIT_LIMIT_EXCEEDED` | 400 | Límite de crédito personal superado |
| `CREDIT_OVERDUE` | 403 | Crédito en mora, bloqueo |
| `CUSTOMER_BLOCKED` | 403 | Cliente bloqueado por mora |
| `CUSTOMER_HAS_OVERDUE_DEBT` | 403 | Deuda vencida activa (bloqueo transversal) |
| `INVALID_PAYMENT_AMOUNT` | 400 | Pago inválido |
| `CREDIT_ALREADY_PAID` | 409 | Crédito pagado totalmente |
| `CREDIT_ALREADY_EXISTS` | 409 | Crédito personal ya existe (máx 1) |
| `INVALID_CREDIT_TYPE` | 400 | Tipo de crédito no soportado |
| `INVALID_TERM` | 400 | Plazo inválido |
| `INVALID_INTEREST_RATE` | 400 | Tasa inválida |
| `CUSTOMER_NOT_FOUND` | 404 | Cliente no encontrado en vista local |
| `PAYMENT_DUPLICATED` | 409 | Pago duplicado o ya procesado |
| `INSUFFICIENT_FUNDS` | 409 | Fondos insuficientes en cuenta origen |
| `RESTRUCTURE_NOT_ALLOWED` | 422 | Crédito no elegible para reestructuración |
| `EXTERNAL_DEPENDENCY_UNAVAILABLE` | 503 | Dependencia no disponible |
| `UNAUTHORIZED` | 401 | Token inválido o ausente |
| `FORBIDDEN_OPERATION` | 403 | Operación prohibida (crédito pagado, cancelado o en mora) |

---

## Reglas Críticas de Implementación

### 1. Aislamiento del Dominio (Obligatorio)

- `domain` **NUNCA** importa clases de `infrastructure` ni `interfaces`.
- `domain.model` **NUNCA** contiene `@Document`, `@Id`, `@Indexed`, `@JsonProperty`.
- `domain.service` **NUNCA** importa `org.springframework.data.*`, `org.springframework.kafka.*`, `org.springframework.web.*`, ni `reactor.core.publisher.*`.
- `domain.port` usa exclusivamente **RxJava 3** (`Single`, `Maybe`, `Completable`, `Flowable`).

### 2. Reactividad No Bloqueante (Obligatorio)

- Prohibido `.block()`, `.blockingGet()`, `.blockingFirst()`, `Thread.sleep()`.
- Prohibido `.subscribe()` dentro de controladores REST.

### 3. Montos Monetarios (Obligatorio)

- Usar `BigDecimal` con escala `2` y `RoundingMode.HALF_UP` para todos los montos.
- **NUNCA** usar `double` o `float` para montos monetarios.

### 4. Fechas y Horarios (Obligatorio)

- Usar `java.time.LocalDateTime`, `LocalDate` o `Instant`.
- **NUNCA** usar `java.util.Date`.

### 5. Idempotencia Kafka (Obligatorio)

- Todo `@KafkaListener` debe verificar y registrar la clave `<topic>:<entityId>:<occurredAt>` en `processed_events`.

### 6. Javadoc en Español (Obligatorio)

- Toda clase y método público debe tener Javadoc en español.

---

## Setup y Ejecución

### Desarrollo

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.21.0.3"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd spring-boot:run
```

### Build y Pruebas

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17.0.21.0.3"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd clean verify
```

### Docker

```bash
docker build -t yanki/credits-service:1.0.0 .
docker run -p 8084:8084 \
  -e MONGODB_URI=mongodb://host.docker.internal:27017/yanki_credits \
  -e REDIS_HOST=host.docker.internal \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  yanki/credits-service:1.0.0
```

### Salud

```bash
curl http://localhost:8084/actuator/health
```

---

## Referencias

| Archivo | Sección | Descripción |
|---------|---------|-------------|
| `README.md` (este servicio) | Documentación funcional y técnica | Endpoints, lógica de negocio, eventos, modelo de datos |
| `test-cases/README.md` | Casos de prueba locales | CP-075 a CP-084 para referencia rápida |
| `../../AGENTS.md` | Reglas de Oro de plataforma | Trazabilidad bidireccional y arquitectura hexagonal |
| `../../system-prompt-auditoria-hexagonal-yanki.md` | System prompt completo | Reglas obligatorias de auditoría hexagonal |
| `../../CASOS_USO/CU-03-Gestion-Creditos.md` | Caso de Uso | Gestión de Créditos |
| `../../CASOS_USO/CU-06-Pagos-Creditos.md` | Caso de Uso | Pagos de Créditos |
| `../../CASOS_PRUEBA/CASOS_PRUEBA_QA.md` | CP-075 a CP-084 | Casos de prueba fuente |
| `../../CASOS_PRUEBA/RTM.md` | Fila CU-03, CU-06 | Matriz de trazabilidad |
| `../../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md` | Sección 3 | Trazabilidad por microservicio |
| `../../DIAGRAMAS_SECUENCIAS/SEC025_apertura_credito_personal.md` | Diagrama | Apertura crédito personal |
| `../../DIAGRAMAS_SECUENCIAS/SEC026_apertura_credito_empresarial.md` | Diagrama | Apertura crédito empresarial |
| `../../DIAGRAMAS_SECUENCIAS/SEC027_pago_credito.md` | Diagrama | Pago crédito |
| `../../DIAGRAMAS_SECUENCIAS/SEC028_job_mora_bloqueo_transversal.md` | Diagrama | Job mora transversal |
| `../../DIAGRAMAS_SECUENCIAS/SEC029_consulta_credito_cronograma.md` | Diagrama | Consulta crédito y cronograma |
| `../../CONVENTIONS.md` | Convenciones técnicas | Stack, estilo de código, testing |
| `../../docs/EVENTOS.md` | Catálogo Kafka | Eventos publicados y consumidos |
| `src/main/java/com/yanki/credits` | Código fuente | Implementación hexagonal completa (75 archivos main + 8 test = 83 total, 82 @Test) |

---

## Historial de Versiones

| Fecha | Versión | Autor | Cambio |
|-------|---------|-------|--------|
| 2026-11-14 | 1.0.0 | Sistema | Creación inicial: especificación técnica y funcional |
| 2026-11-14 | 2.0.0 | Sistema | Auditoría hexagonal completa, diagnóstico de violaciones, plan de reestructuración, reglas críticas, CPs corregidos (CP-075 a CP-084), referencias bidireccionales |
| 2026-11-14 | 2.1.0 | Sistema | Refactorización hexagonal completada: dominio puro, puertos, adaptadores, CRUD completo de Credit, 63 tests pasando, Swagger completo, AUDITORIA.md actualizado |
| 2026-11-14 | 2.2.0 | Sistema | CRUD completo de Payment: UPDATE (corrección) y DELETE (reversa), 12 endpoints, 82 tests, servicio 100% completado |
| 2026-11-22 | 2.3.0 | Sistema | Actualización de documentación: estructura de código corregida, archivos nuevos documentados, endpoints verificados, Kafka topics actualizados |
