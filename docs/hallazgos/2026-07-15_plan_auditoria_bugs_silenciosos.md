# Plan de auditoría — bugs silenciosos (2026-07-15)

**Motivación:** durante la sesión de remediación del 07-15 se encontraron varios bugs graves
**de forma accidental**, no por una auditoría diseñada para buscarlos: el fix de `usuario_creacion`
apareció al investigar una pregunta sobre el ledger hash-encadenado; el bug de `asignar_triggers()`
apareció al *ejecutar* `fn_reconciliar_tabla` en vez de solo leerla; el hueco total de autorización
en `DisponibilidadDocenteController` apareció al comparar endpoints hermanos por simetría; el 500
de `PortalUsuarioController` apareció al rastrear una función hasta su implementación real; el
crash-loop de `Suplencia` apareció solo al *redesplegar* de verdad. Ninguno se habría encontrado
leyendo código de forma aislada y asumiendo que funciona.

Este plan diseña 4 auditorías dirigidas específicamente a esa clase de bug — "el código compila,
los tipos cuadran, pero la conexión entre dos partes del sistema nunca se verificó de verdad."

---

## Resumen de fases

| Fase | Objetivo | Prioridad | Esfuerzo | Método |
|---|---|---|---|---|
| 1 | Drift entidad-JPA vs esquema real de BD | 🔴 Alta | Medio | Script mecánico + verificación puntual |
| 2 | Simetría entre endpoints hermanos (82 controllers) | 🔴 Alta | Alto | 3 agentes paralelos por dominio |
| 4 | Mecanismos "a medias" (GUC/interceptors sin conectar) + runtime | 🟠 Media-alta | Medio | Búsqueda dirigida + smoke tests reales |
| 3 | Frontend Angular — BOLA/BFLA y contrato con backend | 🟡 Media | Alto | Agentes por dominio (frontend) |

**Orden recomendado:** 1 → 2 → 4 → 3 (por relación valor/esfuerzo; el backend ya es el límite de
confianza real tras el endurecimiento de hoy, así que el frontend es el de menor urgencia).

---

## Fase 1 — Drift entidad-JPA vs esquema real de BD

**Por qué:** es exactamente la clase de bug que causó el crash-loop de `Suplencia` (migración
cambia columnas, la entidad JPA no se actualiza) y el bug de `usuario_creacion`/`modificacion`
(121 tablas con un `DEFAULT` de columna que nadie había cruzado contra el trigger). Con 57+
entidades `@Entity` en el sistema, es probable que existan más discrepancias silenciosas que
simplemente no se han disparado todavía (columnas no usadas en el flujo normal, o entidades de
módulos de bajo tráfico nunca ejercidas en esta sesión).

**Método (mecánico, no requiere juicio — automatizable):**
1. Extraer de cada `@Entity` en `backend-spring/src/main/java`: el `@Table(name=...)` y cada
   campo con su `@Column(name=...)` (o el nombre inferido si no tiene anotación), tipo Java, y
   flags `nullable`/`insertable`/`updatable`.
2. Cruzar contra `information_schema.columns` de la tabla real en la BD viva: columnas que la
   entidad mapea pero la tabla NO tiene (→ `SchemaManagementException` garantizado al arrancar
   o al primer INSERT/UPDATE que las toque, como pasó con Suplencia); columnas `NOT NULL` sin
   default en la tabla que la entidad NO mapea (→ el INSERT vía JPA violará el constraint,
   mismo patrón que los 43 bugs de Fase 3 del 07-14, pero para JPA en vez de DTOs); columnas con
   `DEFAULT` a nivel de columna (candidatas al mismo problema de `usuario_creacion` — un
   `DEFAULT` de columna puede tapar silenciosamente un valor que la app cree estar enviando).
3. Para cada discrepancia encontrada, un smoke-test real (INSERT+UPDATE de prueba en una
   transacción con `ROLLBACK`, igual que se hizo hoy para verificar el fix de `usuario_creacion`)
   para confirmar si realmente rompe o si hay un mecanismo de compensación (ej. `@Column(insertable=false)`
   con trigger que lo resuelve correctamente).
4. Salida: tabla de hallazgos con severidad (🔴 rompe al tocar / 🟠 dato oculto silenciosamente /
   🟡 inconsistencia menor).

**Ejecución sugerida:** un script (`scripts/audit-entity-schema-drift.js` o `.py`) es más preciso
y rápido que delegar a agentes — es comparación mecánica de metadatos, no requiere criterio.

---

## Fase 2 — Simetría entre endpoints hermanos

**Por qué:** el R-5 del 07-15 buscó *ausencia total* de autorización (¿llama `resolveUser`? ¿hay
algún chequeo de rol?). Ese criterio no detecta el patrón de `AulaController.eliminarFranja` o
`DisponibilidadDocenteController`: endpoints donde SÍ hay algo de seguridad, pero un hermano
(mismo recurso, operación similar) tiene un nivel de protección distinto sin razón aparente.

**Método:**
1. Por cada controller, agrupar sus `@GetMapping`/`@PostMapping`/`@PutMapping`/`@PatchMapping`/
   `@DeleteMapping` por recurso (mismo path base / mismo tipo de entidad que tocan).
2. Para cada grupo, tabular: ¿llama `resolveUser`? ¿qué `requireX`/chequeo de nivel usa (si
   alguno)? ¿aplica scoping por plantel? ¿verifica ownership (ej. "solo mis propios registros")?
3. Señalar cualquier grupo donde un endpoint tenga un chequeo que sus hermanos no tienen, **sin
   una razón de negocio explícita en el código** (comentario, nombre de método, distinción de
   nivel de acceso intencional documentada).
4. Corregir los reales, dejar anotados los que son diseño legítimo (ej. catálogos de solo
   lectura vs. mutaciones — igual que se decidió hoy con `EvalDocenteController`/criterios).

**Ejecución sugerida:** 3 agentes en paralelo por los mismos 3 lotes de dominio ya usados en R-5
(reutilizar `batch_00`/`batch_01`/`batch_02`), con el checklist explícitamente comparativo
("¿hay asimetría entre hermanos?") en vez de buscar ausencia total. Mismas reglas de alcance:
sin `.md`, sin sub-agentes, sin `docker build/restart` (verificación centralizada al final).

---

## Fase 4 — Mecanismos "a medias" + verificación runtime

**Por qué:** `AuditSessionInterceptor` escribía un GUC de sesión que `fn_auditoria_biu()` nunca
leía — dos piezas del sistema que "debían" estar conectadas pero nunca se verificó en vivo que
lo estuvieran. Es razonable que existan más casos así, en cualquier frontera entre capas
(Spring↔Postgres, Angular↔Spring, Spring↔FastAPI).

**Método (requiere más criterio, menos mecánico que Fase 1):**
1. Buscar otros usos de `set_config`/`current_setting` (GUCs de sesión) y confirmar que quien
   escribe y quien lee coincidan en el nombre exacto de la variable y en el momento del ciclo de
   vida de la transacción.
2. Buscar otros `@Aspect`/interceptors/filters que asuman un efecto en otra capa (ej.
   `AuditHttpFilter`, `RateLimitingFilter`) y confirmar en vivo (no solo leyendo) que el efecto
   esperado ocurre.
3. Correr smoke-tests reales (transacción real contra la BD viva, no mocks de `mvn test`) de los
   flujos de escritura más sensibles — inscripción, calificación, conducta, expediente médico,
   suplencias — verificando que el dato persistido sea exactamente el esperado (incluye
   confirmar que `usuario_modificacion` cambia de verdad en cada uno, replicando la prueba de hoy).
4. Revisar los adaptadores `*FastApiAdapter` (límite hexagonal Spring↔FastAPI, boletas/PDFs) —
   nunca se tocaron hoy; mismo riesgo de "contrato asumido, nunca verificado en vivo" que motivó
   toda la Fase 1-6 del 07-14 para Angular↔Spring.

**Ejecución sugerida:** yo directamente (no delegar a agentes en paralelo) — requiere criterio
para identificar el patrón, no es una lista mecánica de archivos a revisar uno por uno.

---

## Fase 3 — Frontend Angular: BOLA/BFLA y contrato con el backend

**Por qué:** hoy solo se auditó el backend Spring (82 controllers). El frontend nunca recibió el
mismo tratamiento. Con el backend ya endurecido, el frontend deja de ser el límite de confianza
real de seguridad — pero puede haber inconsistencias de UX (botones visibles que el backend ya
rechaza) o, más importante, componentes que **confían** en que el backend valida algo que en
realidad no valida (o que sí valida pero el frontend no maneja el 403 con gracia).

**Método:**
1. Por dominio (mismo criterio de 3 lotes), revisar componentes Angular que hagan mutaciones:
   ¿manejan un 403/404 del backend de forma explícita, o asumen éxito?
   ¿ocultan acciones basándose en `nivelAcceso` del lado cliente sin que el backend replique
   exactamente la misma regla (podría haber discrepancias de umbral)?
2. Cruzar contra los hallazgos de Fase 1-6 del 07-14 (mismatch de contrato camelCase/snake_case)
   — confirmar que los 35+47 controllers ya auditados hoy no introdujeron un nuevo mismatch de
   nombres al agregar los chequeos (los agentes de hoy ya verificaron compilación Java, pero no
   verificaron el lado Angular que consume esos endpoints).

**Ejecución sugerida:** agentes por dominio, alcance limitado a componentes Angular + sus
servicios HTTP asociados, mismas reglas anti scope-creep.

---

## Estimación de esfuerzo total

- Fase 1: 2-4 horas (script + smoke-tests de lo encontrado).
- Fase 2: comparable a R-5 de hoy (~3 agentes, 1-2 rondas) — medio día.
- Fase 4: 2-3 horas de revisión dirigida + smoke-tests.
- Fase 3: comparable a Fase 2 pero en Angular — medio día.

## Criterio de éxito

Igual que hoy: cualquier hallazgo se considera cerrado solo cuando (a) el fix compila, (b)
`mvn test` sigue en 555/555 (o el número vigente), (c) el contenedor redesplegado queda estable
sin crash-loop, y (d) para hallazgos de datos/auditoría, una prueba empírica real (INSERT/UPDATE
con `ROLLBACK`, o llamada API real) confirma el comportamiento — no basta con que el código "se
vea correcto".
