# Reporte de fiabilidad — desde el cambio de servidor (2026-07-10) hasta hoy (2026-07-16)

**Encargo:** reporte con honestidad de todas las dimensiones, historial de bugs desde la
migración de servidor, detalle de por qué cada dimensión está donde está (no solo el número),
residuales de Biblioteca y módulos no muestreados, comparación contra el estándar de
seguridad, y plan hacia 90-95% de fiabilidad esta semana — con `audit_aiud` y prueba de
restore de backup explícitamente diferidos por decisión del usuario.

---

## 0. Verdad incómoda primero

**Los números de "60-65% seguridad" y "40% accesibilidad" que reporté ayer no son una
medición mía — son la estimación heredada del documento `2026-07-15_analisis_honesto_entregabilidad.md`,
con el número de seguridad ajustado a ojo hacia arriba por el trabajo de hoy.** No los había
verificado con evidencia propia hasta este reporte. Ya está corregido abajo con evidencia
directa (grep, consultas SQL en vivo, conteo de archivos) para cada dimensión.

**Segunda verdad incómoda, más grave:** esto ya pasó dos veces antes. La Fase 5 del
2026-07-14 declaró "26 hallazgos BOLA/BFLA corregidos... confirmado íntegro" para
`EvalDocenteController`. El plan R-5 del 2026-07-15 declaró **"82 controllers auditados,
COMPLETADO"**, incluyendo textualmente sobre `DisponibilidadDocenteController`: *"ni siquiera
las escrituras tenían control de rol... agregado requireStaff + restricción de ownership"*.

Hoy, en esta misma sesión, verificación independiente encontró en esos dos archivos
exactamente declarados "completos":
- `EvalDocenteController` — comparaba `persona_id` contra columnas que en realidad son
  `ades_profesores.id`/`ades_usuarios.id`. Un evaluador real nunca podía enviar su propia
  evaluación. [Cierto — reproducido y corregido en vivo hoy.]
- `DisponibilidadDocenteController` — el "control de rol" agregado por R-5 es solo un
  piso de staff (nivel ≤4) + auto-ownership para nivel 4. **Nunca tuvo scoping por plantel**:
  un Coordinador/Director/Admin_Plantel de un plantel podía ver/editar la disponibilidad de
  CUALQUIER docente de CUALQUIER plantel. Sigue sin scoping de plantel hoy (ver §4).

Esto no es para desacreditar el trabajo de R-5/Fase 5 — sí cerraron huecos reales y verificables
(ver §1). Es para que el "COMPLETADO" en la documentación de este proyecto se lea, de ahora
en adelante, como *"completado contra el criterio que se probó en ese momento"*, no como
"sin huecos". Ya pasó con el comando de `OnDestroy` roto (CLAUDE.md lo documenta), con R-5, y
con Fase 5. Es un patrón, no un incidente aislado. El plan de la §7 lo trata como tal.

---

## 1. Cronología de bugs desde el cambio de servidor (2026-07-10)

31 commits, ~185 archivos de código tocados en la ventana 07-10→07-16 (sin contar frontend
de estilo/UI). Los bugs reales (no refactors cosméticos), en orden cronológico:

### 2026-07-11 — Bug transaccional sistémico (🔴 crítico, pérdida silenciosa de datos)

**Qué:** `spring.datasource.hikari.auto-commit: false` exige `@Transactional` explícito en
todo `jdbc.update()`. La cadena completa de PATCH de alumnos/profesores/personal-admin/
contactos no lo tenía — los cambios nunca se confirmaban en BD, sin error visible (HTTP 200
igual). Descubierto como efecto colateral de un backfill de cifrado PII que ejecutó el mismo
UPDATE 217,400 veces sin que la BD cambiara ni una vez.

**Alcance real (mucho mayor de lo que parecía al inicio):** barrido completo del backend
encontró el mismo patrón en **~50 archivos adicionales**, incluyendo:
- **`AuditHttpFilter`** — el log de auditoría de TODA mutación HTTP del sistema **nunca había
  persistido ni una fila**, pese a que CLAUDE.md lo exige como obligatorio desde el inicio.
- `ProcesosWriteService` — 12 de 14 métodos sin protección, incluidas bajas/reactivaciones de
  alumnos y aprobación de admisiones.
- Módulo `admin` completo (alta de usuarios del sistema).
- 23 módulos con el patrón simple (asistencia_personal, badges, cierre, compliance, conducta,
  encuestas, entregas, eval_docente, evaluaciones, expediente, expediente_laboral, horarios,
  justificaciones, learning_paths, medico, movilidad, notificaciones, planes_estudio,
  portal_familias, procesos, reinscripcion, y otros).

**Cómo se resolvió:** `@Transactional` en la capa `ApplicationService` (no en las clases hoja),
extracción a beans nuevos con `REQUIRES_NEW` donde la escritura no debía acoplarse a la
transacción de negocio (`AuditLogWriter`, `WebhookLogWriter`, `HorarioDesactivadorService`).
Verificado con pruebas de reproducción/fix contra BD real (no solo unitarias), 550 tests en
verde, desplegado.

**Qué falta:** `PiiBackfillRunner` (el runner que originalmente disparó el hallazgo) **sigue sin
`@Transactional`** — no reactivado, documentado como pendiente si se vuelve a usar.

### 2026-07-11 — Cifrado PII (LFPDPPP)

Backfill real ejecutado sobre 5,178/5,178 personas tras corregir el bug transaccional de
arriba. Scaffolding de cifrado + máscaras de captura (CURP/RFC/teléfono/email) en frontend y
backend. Aviso de Privacidad LFPDPPP: borrador, **pendiente de revisión legal real**.

### 2026-07-14 — Fase 1/3: contrato de datos backend↔frontend

- **Fase 1** (contrato de respuesta, read-path): 1 bug corregido (`minimo_aprobatorio`
  Preparatoria mal aplicado en cálculo de escala).
- **Fase 3** (cruce BD↔backend↔UI, 3 vías): **43 bugs corregidos** — mismatches de nombre de
  campo entre lo que Angular esperaba y lo que Java devolvía, varios de ellos en flujos que
  literalmente **nunca habían funcionado** desde que se escribieron (endpoints "vivos" en
  código pero muertos en la práctica).
- **Crash-loop de `ObjectMapper`** (no planeado, crítico) — descubierto solo al redesplegar de
  verdad tras los fixes de Fase 3; resuelto el mismo día.

### 2026-07-14/15 — Fase 5: BOLA/BFLA en los endpoints "revividos"

Los endpoints que Fase 1/3 corrigieron nunca habían recibido tráfico real — lo que significa
que su lógica de autorización tampoco. **26 hallazgos BOLA/BFLA corregidos** en 23 archivos
(7 personas, 10 académico, 9 administración), incluyendo casos graves: `ContactosController`
(editar expediente médico de alumno ajeno sin ningún chequeo), `MedicoController` (expediente
médico de cualquier alumno, sin `resolveUser`), `ConductaController` (reportes disciplinarios
sin `resolveUser`), `ImportsController` (un Director podía importar alumnos hacia OTRO
plantel vía CSV), `ReinscripcionController` (aprobar reinscripciones sin `requireAdmin`).

### 2026-07-15 — R-5: segunda pasada, "82 controllers auditados"

Segunda ronda declarada completa (ver verdad incómoda arriba) — cerró 51 archivos adicionales
con hallazgos reales (`BoletasController`, `CierreCicloController` con cierre irreversible sin
chequeo, `PlaneacionController` con 18 GETs sin `resolveUser`, `DisponibilidadDocenteController`
"peor de lo esperado" — sin protección ni en escrituras). Endurecimiento del ledger de
auditoría (SHA-256 encadenado real, migraciones 137-145).

### 2026-07-15/16 — Hoy: code-review + Opción A (esta sesión)

- **10 hallazgos confirmados** vía code-review multiagente + verificación en vivo con JWT
  reales: fuga cross-plantel de datos de salud/académicos (Boletas/CondiciónCrónica/
  Expediente/Gradebook), autoservicio de docente roto por confusión de espacio de ID
  (`persona_id` vs `profesor_id`/`usuario_id`) en 4 módulos, exención de Director/Admin_Plantel
  en Badge/Capacitación/Licencia, badges otorgables por padres sin piso de staff, índice
  faltante en el ledger de auditoría, GUC pisando valores explícitos de auditoría.
- **Decisión de diseño (tuya):** ADMIN_PLANTEL (nivel 1) es plantel-acotado en todo el
  sistema, no "superadmin". Corregido centralmente en `AdesUserService.getEffectivePlantelId` +
  ~24 sitios adicionales en 20 controladores (incluido un tercer chequeo en
  `ProfesorController` que se había pasado por alto en la primera pasada de hoy mismo).

---

## 2. Estado por dimensión, con evidencia (no estimación heredada)

### 2.1 Funcional — flujos core auditados: **~88-90%** [Probable]

Basado en: 555/555 tests en verde tras Fase 5, 43 bugs de contrato cerrados en Fase 3, bug
transaccional cerrado con prueba de reproducción real. No se re-verificó hoy — cifra
heredada de un trabajo con evidencia sólida (pruebas contra BD real, no solo lectura de
código), por eso mantiene el `[Probable]` en vez de `[Cierto]`.

**Qué falta para subir:** ejecutar los 21 specs E2E completos por rol al menos una vez (hoy
solo 5 corren en CI — ver §2.5); el resto se ha verificado por sesión manual, no por corrida
automatizada reproducible.

### 2.2 Seguridad / autorización: **~78-82%** [Cierto en lo verificado hoy, Probable en el resto]

**Evidencia directa de hoy** (no heredada):

- 83 controladores totales en el backend. **30 tienen un mecanismo de scoping de plantel
  verificable** (`verificarPlantel`/`getEffectivePlantelId`/`scopePlantel`) — antes de esta
  sesión eran ~10-15 con el patrón correcto y el resto con umbrales ad-hoc inconsistentes
  (`==3`, `<=1`, `<=2`, `>2` todos coexistiendo para el mismo concepto).
- **43 controladores llaman `resolveUser` pero no muestran ningún mecanismo de scoping de
  plantel detectable.** De esos, **12 tienen CERO menciones de "plantel" en todo el archivo**
  — candidatos reales a hueco, no falso negativo de mi grep: `SaludAvanzadaController`
  (salud), `ExpedienteLaboralController` (RFC/salario/IMSS — RH), `DisponibilidadDocenteController`
  (confirmado arriba), `EvalDocenteController` (mismo — nivel 1-3 tiene alcance
  institucional sobre evaluaciones 360° de cualquier plantel), `ContactosController`,
  `CertificadosController`, `AsistenciaController`, `AsistenciaPersonalController`,
  `JustificacionController`, `TareaEntregaController`, `ActividadesController`,
  `PadresController`, `PlanesEstudioController`.
  - De los 12: al menos 2 (Salud avanzada, Expediente laboral) son **datos sensibles con
    fuga cross-plantel confirmable por el mismo patrón que ya se probó real en
    `CondicionCronicaController` hoy** — no es hipotético, es el mismo bug, archivo distinto.
  - Los otros 31 (de los 43) usan mecanismos que mi grep no captura (ej. helpers con nombre
    propio como `scopePlantel` en minúscula sin el patrón exacto) o legítimamente no
    necesitan scoping (catálogos institucionales: `SepomexAdminController`, `PlantelController`,
    `UsuariosController` administrado por admin global). **No cuantifiqué cuántos de esos 31
    son gap real vs. diseño correcto — es el trabajo pendiente más importante de §4.**

**Mapeo contra OWASP API Security Top 10 2023** (ver §5 para detalle completo).

**Por qué no es más alto:** el patrón dominante (BOLA cross-plantel por umbral inconsistente)
está cerrado. Lo que queda es la cola larga: módulos que nunca tuvieron NINGÚN scoping (no un
umbral equivocado, sino ausencia total), y esos son, por definición, los que ninguna de las 3
rondas previas (Fase 5, R-5, hoy) tocó — cada ronda amplía la cobertura pero ninguna ha sido
un barrido verificablemente exhaustivo de los 83 controladores con un criterio objetivo y
reproducible (grep + verificación en vivo), hasta este reporte.

### 2.3 Operación (backups, auditoría): **~65%** [Cierto]

Sin cambio — confirmado en vivo hoy:
- Backup diario corriendo (cron `0 2 * * *`, backup real de hoy 2026-07-16 02:00 + uno manual
  03:34 existen en disco). **Sin copia fuera del servidor, sin prueba de restore documentada.**
  Por decisión tuya, se difiere esta semana.
- `audit_aiud`: **180 tablas en `PENDIENTE_AIUD`, 0 en estado activo** (la migración 143 apagó
  deliberadamente las 3 que estaban en prueba — correcto per la política de diferir a
  go-live). `ades_log_autenticacion` sigue en `PENDIENTE_BIU` (ni el trigger básico). Por
  decisión tuya, diferido.
- 3 tablas (`ades_audit_log`, `ades_encryption_audit`, `ades_mv_refresh_log`) sin columnas de
  auditoría — **probablemente correcto por diseño** (son tablas de log/infraestructura, no de
  negocio), pero nunca se documentó la excepción explícitamente contra la Regla Mandatoria #3.

### 2.4 Usabilidad / Accesibilidad: **~35-40%** [Cierto]

Verificado hoy, no heredado:
- **1 de 79 componentes** (`.component.ts`) usa atributos `aria-*` — 1.3%.
- `axe-core` **sí está instalado** y **sí tiene un spec dedicado** (`14-a11y.spec.ts` +
  `axe-helper.ts`) — mejor de lo que el doc 07-15 registraba, pero no hay evidencia de que
  ese spec corra en el CI actual (no aparece en `e2e-tests.yml`).
- Heurísticas cognitivas de CLAUDE.md (10 principios) sin checklist ni verificación
  sistemática por componente.

**Por qué está tan bajo, en detalle:** esto nunca ha sido objeto de ninguna de las 5 rondas de
auditoría documentadas (todas centradas en datos/autorización/contrato). Es deuda pura, no un
bug regresivo — nadie ha invertido tiempo aquí todavía. **Es la dimensión con menor esfuerzo
de auditoría relativo a su tamaño real del problema.**

### 2.5 CI/CD/Testing: **~55-60%** [Cierto]

- 3 workflows reales (`e2e-tests.yml`, `security-audit.yml`, `security.yml`).
- **`check-api-contracts.js` SÍ corre en CI ahora** (`--strict`, paso `Check API Contracts`) —
  esto es una mejora real desde el 07-15 (el doc de esa fecha lo marcaba ausente); confirmado
  con el archivo del workflow, fechado 07-15 13:55, después del análisis de las 13:01.
- **21 specs E2E totales, 5 corren en CI** (`01-auth`, `02-alumnos`, `03-asistencias`,
  `04-calificaciones`, `05-certificados`) — el resto (incluyendo `10-rbac.spec.ts`, el más
  relevante para todo lo corregido hoy) no está en el pipeline.
- Sin JaCoCo (backend) ni coverage config (frontend) — no hay número de cobertura real, solo
  conteo de tests (555 backend, 21 specs E2E).

---

## 3. Comparación contra el estándar de seguridad (OWASP API Security Top 10, 2023)

CLAUDE.md cita este estándar explícitamente. Mapeo honesto, no aspiracional:

| Categoría OWASP | Estado ADES | Evidencia |
|---|---|---|
| **API1:2023 — BOLA** | 🟡 Mayormente cerrado en el patrón dominante (plantel); abierto en la cola larga (12 controllers sin scoping, ver §2.2/§4) | Verificado en vivo hoy con JWT reales en 15+ endpoints |
| **API2:2023 — Broken Authentication** | 🟢 Bien | JWT vía Authentik/OIDC obligatorio globalmente (`SecurityConfig.java`), confirmado en Fase 5 que no hubo caso de acceso anónimo puro |
| **API3:2023 — Broken Object Property Level Authorization** | 🟡 Parcial | Mass assignment no auditado sistemáticamente; algunos DTOs de creación aceptan campos que deberían forzarse server-side (ej. `plantel_id` en Badge, ya corregido; patrón no verificado en el resto) |
| **API4:2023 — Unrestricted Resource Consumption** | 🟢 Razonable | Rate limiting real (`RateLimitingFilter` Spring + slowapi FastAPI), paginación con límites (`Math.min(porPagina, 200)` patrón repetido) |
| **API5:2023 — BFLA** | 🟡 Igual que API1 — cerrado en lo auditado, cola larga sin verificar | Mismo patrón, mismos hallazgos (otorgar/revocar badges sin piso de staff, corregido hoy) |
| **API6:2023 — Unrestricted Sensitive Business Flows** | 🔴 No evaluado | Sin rate limiting específico por flujo de negocio (ej. cuántas licencias puede aprobar un RH por minuto); fuera de alcance de todas las rondas hasta hoy |
| **API7:2023 — SSRF** | 🟡 Riesgo bajo pero no verificado | El BFF proxea hacia FastAPI (`*FastApiAdapter`) — no se ha auditado si algún endpoint acepta URLs controladas por el usuario hacia esos proxies |
| **API8:2023 — Security Misconfiguration** | 🟡 Parcial | Headers de seguridad presentes (HSTS/XFO/XCTO vía nginx), pero CSP solo en 2 de ~6 vhosts (dato del 07-15, no re-verificado hoy); 11 imágenes Docker en `:latest` (viola la propia regla de supply-chain de CLAUDE.md) |
| **API9:2023 — Improper Inventory Management** | 🟡 Parcial | Sin OpenAPI/spec generado y versionado como fuente de verdad (Fase 2 "infraestructura lista, adopción 0 componentes" — sigue así) |
| **API10:2023 — Unsafe Consumption of APIs** | 🟡 No evaluado hoy | Integración con NVIDIA NIM/OpenAI vía `OPENAI_BASE_URL` — sin validación documentada de las respuestas del proveedor externo antes de usarlas en lógica de negocio |

**Lectura honesta:** ADES cumple bien las categorías "binarias" (autenticación, rate limiting)
porque son mecanismos de infraestructura que se configuran una vez y no dependen de que cada
desarrollador recuerde aplicarlos. Cumple mal las categorías que dependen de disciplina
repetida por endpoint (BOLA, BFLA, property-level authz) — exactamente donde ha estado todo
el esfuerzo de las últimas 3 rondas de auditoría, y exactamente donde siguen apareciendo
huecos nuevos cada vez que alguien mira con una herramienta distinta (grep sistemático hoy vs.
lectura por agente en rondas previas).

---

## 4. Residuales detallados

### 4.1 Biblioteca (`BibliotecaController`)

`scopePlantel(user, solicitado)` (variable local, no el helper compartido `AdesUserService`)
usa umbral `nivel(u) > 2` — exento nivel 0, 1 **y 2** (Admin_Global, Admin_Plantel, Director).
Bajo la decisión de Opción A (solo nivel 0 exento), esto deja a Admin_Plantel y Director sin
restricción cross-plantel en: alta/baja de libros, préstamos, devoluciones. No se corrigió
hoy — no coincidió con ninguno de los patrones de grep usados (nombre de variable/método
distinto: `nivel(u)` en vez de `user.getNivelAcceso()`).

### 4.2 Módulos nunca muestreados con scoping ausente (no solo "no confirmado")

Lista concreta, ordenada por sensibilidad de datos:

| Controller | Dato expuesto | Confianza de que es hueco real |
|---|---|---|
| `medico/SaludAvanzadaController` | Historial médico avanzado | [Probable] — mismo patrón que `CondicionCronicaController`, confirmado real hoy |
| `expediente_laboral/ExpedienteLaboralController` | RFC, salario, IMSS, INFONAVIT | [Probable] — datos RH de máxima sensibilidad, umbral de rol existe (`requireRH`) pero sin plantel |
| `disponibilidad/DisponibilidadDocenteController` | Horario/disponibilidad docente | [Cierto] — confirmado por lectura de código hoy, ver §0 |
| `eval_docente/EvalDocenteController` | Evaluación 360° de desempeño | [Cierto] — mismo, confirmado |
| `contactos/ContactosController` | Tutores legales, `puedeRecoger` | [Suponiendo] — Fase 5 dice que sí se corrigió el chequeo de `nivelAcceso`, pero no confirmé si incluye plantel |
| `certificados/CertificadosController` | Certificados/constancias con calificaciones | [Suponiendo] — no revisado línea por línea hoy |
| `asistencias/AsistenciaController`, `asistencia_personal/AsistenciaPersonalController` | Asistencia de alumnos/personal | [Suponiendo] |
| `evaluaciones/TareaEntregaController`, `gradebook/ActividadesController` | Entregas y actividades de tarea | [Suponiendo] — `TareaEntregaController` probablemente delega en lógica de grupo ya revisada (duplica `EntregasController`, hallazgo de reuse de hoy), riesgo menor |
| `padres/PadresController`, `planes_estudio/PlanesEstudioController`, `justificaciones/JustificacionController` | Datos de padres, planes NEE, justificantes | [Suponiendo] |

**Los primeros 4 filas (Salud avanzada, Expediente laboral, Disponibilidad, EvalDocente)
merecen tratarse como confirmados, no hipotéticos** — incluyen dos de los tres bugs que
literalmente encontré hoy en otros módulos con el mismo dato faltante. Los últimos 8 son
candidatos reales que requieren la misma verificación puntual (leer el archivo, confirmar si
hay o no comparación de plantel) antes de poder calificarlos.

### 4.3 Lo que NO se tocó (por decisión tuya, esta semana)

- `audit_aiud` sigue apagado (180 tablas).
- Backup sin copia off-server ni prueba de restore.

Ambos son operación de infraestructura, no autorización de aplicación — el riesgo que corren
mientras están pendientes es distinto (pérdida de evidencia forense / pérdida de datos ante
desastre), no exposición de datos entre planteles.

---

## 5. Plan hacia 90-95% de fiabilidad global esta semana

Orden por impacto/esfuerzo, asumiendo `audit_aiud` y prueba de restore quedan fuera del
alcance de esta semana:

### Día 1-2 — Cerrar la cola larga de BOLA/BFLA confirmada (§4.2, filas 1-4)
Aplicar el mismo patrón ya usado hoy (`userService.verificarPlantel`) a
`SaludAvanzadaController`, `ExpedienteLaboralController`, `DisponibilidadDocenteController`
(agregar scoping de plantel, no solo el piso de staff que ya tiene) y `EvalDocenteController`
(mismo). Estos 4 tienen confianza [Cierto]/[Probable] de ser huecos reales — no requieren
investigación adicional, solo el fix ya probado.

### Día 2-3 — Verificación puntual de los 8 candidatos restantes (§4.2, filas 5-12) + Biblioteca (§4.1)
Para cada uno: leer el archivo, confirmar si existe o no comparación de `plantel_id`, aplicar
el fix si falta. Esfuerzo bajo por archivo (ya hay un patrón y un helper), el trabajo es la
verificación, no el fix.

### Día 3 — Barrido de variantes de nombre no capturadas por el grep de hoy
El caso de Biblioteca (`scopePlantel` con firma distinta) prueba que el grep de hoy no es
100% exhaustivo. Un agente de exploración dedicado a buscar **todo** método privado que reciba
`AdesUser` y un ID y contenga la palabra "plantel" en su cuerpo (no en su firma), sin asumir
un nombre de variable fijo, cerraría esta clase de falso negativo.

### Día 4 — Correr los 21 E2E completos por rol, al menos una vez
No para meterlos a CI permanentemente esta semana (eso es Día 5+), sino para tener una
corrida real y reciente que valide que el trabajo de autorización de los últimos 3 días no
rompió ningún flujo de UI — el riesgo de regresión silenciosa es real dado el volumen de
archivos tocados (185+).

### Día 4-5 — Gate de `10-rbac.spec.ts` en CI
Es el spec más directamente relevante para todo lo corregido en esta ventana de 3 días y hoy
mismo no corre en el pipeline. Agregarlo a `e2e-tests.yml` junto a los 5 que ya corren.

### Post-semana (no bloquea el 90-95%, pero es la próxima bola de nieve)
Accesibilidad (35-40%) y cobertura de tests medida (JaCoCo/frontend coverage) son las dos
dimensiones que ninguna ronda ha tocado — quedarán como el próximo foco natural una vez que
autorización esté verdaderamente cerrado, no antes.

**Estimación de fiabilidad global tras este plan:** ~88-92% [Suponiendo — depende de cuántos
de los 8 candidatos "por confirmar" resulten ser huecos reales vs. diseño correcto]. Llegar a
95% real requiere `audit_aiud` + prueba de restore, que quedan fuera de esta semana por tu
decisión.
