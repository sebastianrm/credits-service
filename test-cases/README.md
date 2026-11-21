# Casos de Prueba — Credits Service

**Microservicio:** credits-service (puerto 8084)  
**Caso de Uso Owner:** CU-03 (Gestión de Créditos), CU-06 (Pagos de Créditos)  
**Diagramas de Secuencia:** SEC025, SEC026, SEC027, SEC028, SEC029  

---

## Resumen

| CP | Tipo | Descripción | CU | SEC |
|----|------|-------------|----|-----|
| CP-075 | Happy Path | Apertura de crédito personal exitosa | CU-03 | SEC025 |
| CP-076 | Error | Apertura crédito personal rechazada: ya tiene crédito personal | CU-03 | SEC025 |
| CP-077 | Error | Apertura crédito personal rechazada: cliente con deuda vencida | CU-03 | SEC025 |
| CP-078 | Happy Path | Apertura crédito empresarial exitosa (múltiples permitidos) | CU-03 | SEC026 |
| CP-079 | Happy Path | Pago de crédito exitoso desde cuenta propia (event-driven) | CU-06 | SEC027 |
| CP-080 | Error | Pago de crédito rechazado: fondos insuficientes en cuenta origen | CU-06 | SEC027 |
| CP-081 | Happy Path | Pago de crédito efectivo/terceros (sin débito cuenta) | CU-06 | SEC027 |
| CP-082 | Happy Path (Job) | Job diario mora >30 días: detección y bloqueo transversal | CU-03 | SEC028 |
| CP-083 | Happy Path | Consulta de crédito con cronograma (Redis Cache) | CU-03 | SEC029 |
| CP-084 | Happy Path | Consulta de cronograma de pagos específico | CU-03 | SEC029 |

---

## Casos de Prueba Detallados

### CP-075 — Apertura de crédito personal exitosa

- **CU:** CU-03 | **SEC:** SEC025_apertura_credito_personal.md
- **Tipo:** Happy Path
- **Precondiciones:** Cliente PERSONAL ACTIVE, sin crédito personal vigente, sin deuda vencida (`hasOverdueDebt=false`), scoring aprueba.
- **Pasos:**
  1. `POST /api/credits {customerId, type:PERSONAL, amount:5000, termMonths:24, interestRate:12.5}`.
  2. Validar `201 Created` con `creditId`, `creditNumber`, `status=APPROVED`, `outstandingBalance=5000`, `schedule` generado.
  3. Verificar Redis: `yanki:credits:schedule:{creditId}` poblado (TTL configurable).
  4. Verificar evento `yanki.credit.opened.v1`.
- **Postcondiciones:** Crédito activo con cronograma.

---

### CP-076 — Apertura crédito personal rechazada: ya tiene crédito personal (RN: 1 por PERSONA)

- **CU:** CU-03 | **SEC:** SEC025_apertura_credito_personal.md
- **Tipo:** Error / Regla de negocio
- **Precondiciones:** Cliente PERSONAL ya tiene crédito tipo PERSONAL status ACTIVE/APPROVED.
- **Pasos:** Solicitar nuevo crédito personal.
- **Errores esperados:** HTTP `409`, código `CREDIT_ALREADY_EXISTS`.

---

### CP-077 — Apertura crédito personal rechazada: cliente con deuda vencida

- **CU:** CU-03 | **SEC:** SEC025_apertura_credito_personal.md
- **Tipo:** Error / Bloqueo transversal
- **Precondiciones:** `customer_risk_view.hasOverdueDebt=true`.
- **Pasos:** Solicitar crédito.
- **Errores esperados:** HTTP `403`, código `CUSTOMER_HAS_OVERDUE_DEBT`.

---

### CP-078 — Apertura crédito empresarial exitosa (múltiples permitidos)

- **CU:** CU-03 | **SEC:** SEC026_apertura_credito_empresarial.md
- **Tipo:** Happy Path
- **Precondiciones:** Cliente ENTERPRISE ACTIVE.
- **Pasos:** `POST /api/credits {type:ENTERPRISE, amount:50000, termMonths:36, interestRate:10.5, purpose:"Capital de trabajo"}` → `201 Created`.

---

### CP-079 — Pago de crédito exitoso desde cuenta propia (event-driven)

- **CU:** CU-06 | **SEC:** SEC027_pago_credito.md
- **Tipo:** Happy Path
- **Precondiciones:** Crédito ACTIVE con cuota pendiente, cuenta origen con fondos, sin deuda vencida.
- **Pasos:**
  1. `POST /api/credits/{creditId}/payments {amount:500, sourceAccountId, description:"Pago cuota 1"}`.
  2. Validar `201 Created` con `paymentId`, `newBalance`, `nextDueDate`.
  3. Verificar evento `yanki.debit.payment.requested.v1` → accounts-service liquida → `debit.payment.settled.v1`.
  4. Verificar evento `yanki.credit.payment.registered.v1` con desglose capital/interés/mora.
- **Postcondiciones:** Cuota aplicada, saldo pendiente reducido.

---

### CP-080 — Pago de crédito rechazado: fondos insuficientes en cuenta origen

- **CU:** CU-06 | **SEC:** SEC027_pago_credito.md
- **Tipo:** Error
- **Precondiciones:** Cuenta origen sin saldo para amount.
- **Pasos:** Pago desde cuenta propia.
- **Errores esperados:** HTTP `409`, código `INSUFFICIENT_FUNDS` (vía debit.payment.settled REJECTED).

---

### CP-081 — Pago de crédito efectivo/terceros (sin débito cuenta)

- **CU:** CU-06 | **SEC:** SEC027_pago_credito.md
- **Tipo:** Happy Path
- **Precondiciones:** Crédito ACTIVE.
- **Pasos:** `POST /api/credits/{id}/payments {amount, source:THIRD_PARTY}` → `201` sin evento debit.payment.

---

### CP-082 — Job diario mora >30 días: detección y bloqueo transversal

- **CU:** CU-03 | **SEC:** SEC028_job_mora_bloqueo_transversal.md
- **Tipo:** Happy Path (Job Scheduler)
- **Precondiciones:** Existen créditos con cuotas vencidas >30 días, scheduler ejecuta a las 02:00 UTC-5.
- **Pasos:**
  1. Trigger job `overdue-detection`.
  2. Verificar créditos actualizados a `status=OVERDUE`.
  3. Verificar evento `yanki.credit.overdue.v1` publicado por cada crédito en mora (key=customerId).
  4. Verificar accounts/cards/wallet services consumen → actualizan `customer_risk_view.hasOverdueDebt=true`.
  5. Verificar efecto bloqueo: nueva apertura cuenta → RECHAZADA, emisión tarjeta → RECHAZADA, wallet top-up → RECHAZADO.
- **Postcondiciones:** Bloqueo transversal activado.

---

### CP-083 — Consulta de crédito con cronograma (Redis Cache)

- **CU:** CU-03 | **SEC:** SEC029_consulta_credito_cronograma.md
- **Tipo:** Happy Path
- **Precondiciones:** Crédito existe, cronograma en Redis `yanki:credits:schedule:{creditId}`.
- **Pasos:** `GET /api/credits/{id}` → `200` con schedule desde cache (HIT) o MongoDB (MISS).

---

### CP-084 — Consulta de cronograma de pagos específico

- **CU:** CU-03 | **SEC:** SEC029_consulta_credito_cronograma.md
- **Tipo:** Happy Path
- **Precondiciones:** Crédito existe.
- **Pasos:** `GET /api/credits/{id}/schedule` → `200` con array de cuotas (numero, fechaVencimiento, capital, interes, total, estado).

---

## Referencias

| Archivo | Sección | Descripción |
|---------|---------|-------------|
| `../../CASOS_USO/CU-03-*.md` | Caso de Uso completo | Gestión de Créditos (Personal, Empresarial, Hipotecario) |
| `../../CASOS_USO/CU-06-*.md` | Caso de Uso completo | Pagos de Créditos (propia/terceros, event-driven) |
| `../../DIAGRAMAS_SECUENCIAS/SEC025_apertura_credito_personal.md` | Diagrama completo | Apertura crédito personal con scoring y cronograma |
| `../../DIAGRAMAS_SECUENCIAS/SEC026_apertura_credito_empresarial.md` | Diagrama completo | Apertura crédito empresarial con múltiples permitidos |
| `../../DIAGRAMAS_SECUENCIAS/SEC027_pago_credito.md` | Diagrama completo | Pago crédito propia/terceros, aplicación priorizada |
| `../../DIAGRAMAS_SECUENCIAS/SEC028_job_mora_bloqueo_transversal.md` | Diagrama completo | Job mora >30d, publicación overdue, bloqueo transversal |
| `../../DIAGRAMAS_SECUENCIAS/SEC029_consulta_credito_cronograma.md` | Diagrama completo | Consulta crédito + cronograma con Redis Cache |
| `../../CASOS_PRUEBA/CASOS_PRUEBA_QA.md` | CP-075 a CP-084 | Casos de prueba fuente |
| `../../CASOS_PRUEBA/RTM.md` | Fila CU-03, CU-06 | Matriz de trazabilidad completa |
| `../../CASOS_PRUEBA/TRACEABILIDAD_POR_SERVICIO.md` | Sección 3 (credits-service) | Trazabilidad por microservicio |
