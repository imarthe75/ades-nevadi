# Hallazgo: PATCH de alumnos/profesores/personal-admin/contactos no persistía

**Fecha del hallazgo:** 2026-07-11
**Fecha de corrección:** 2026-07-11 (misma sesión, con autorización explícita)
**Severidad:** 🔴 Alta — pérdida silenciosa de datos en producción
**Estado:** ✅ **CONFIRMADO y CORREGIDO** — desplegado en `ades-bff`

## Resumen

`HikariCP` está configurado con `spring.datasource.hikari.auto-commit: false`
(`backend-spring/src/main/resources/application.yml:50`). En este proyecto eso es
una decisión deliberada: **todo `jdbc.update()` debe correr dentro de un método
anotado `@Transactional`**, o el cambio nunca se confirma (commit) y se pierde
cuando la conexión regresa al pool. Docenas de servicios de escritura en el
proyecto siguen este patrón correctamente (`RiesgoConductualService`,
`BibliotecaApplicationService`, `ProcesosWriteService`, `SistemaWriteService`,
`CalificacionPersistenceAdapter`, etc.).

**Pero la cadena completa de PATCH de alumnos, profesores, personal
administrativo y contactos familiares NO tenía `@Transactional` en ningún
punto:**

| Archivo | Método | Antes | Ahora |
|---|---|---|---|
| `modules/alumnos/application/service/AlumnoApplicationService.java` | `crear`, `actualizar` | ❌ | ✅ |
| `modules/profesores/application/service/ProfesorApplicationService.java` | `crear`, `actualizar` | ❌ | ✅ |
| `modules/personal_admin/application/service/PersonalAdminApplicationService.java` | `registrar`, `actualizar`, `desactivar` | ❌ | ✅ |
| `modules/contactos/application/service/ContactosApplicationService.java` | `registrar`, `actualizar`, `eliminar`, `expedienteMedico`, `actualizarExpedienteMedico`, `upsertDocEstatus` | ❌ | ✅ |

`shared/persona/PersonaUpdateHelper.java` y `PersonalAdminPersistenceAdapter.java`
(las clases "hoja" que hacen el `jdbc.update()`) se dejaron **sin**
`@Transactional` propio — correcto: el `@Transactional` va en el método público
del bean gestionado por Spring que las invoca (capa `ApplicationService`), y
Spring propaga esa misma transacción a las llamadas internas a estos helpers
(propagación `REQUIRED`, el default). Ponerlo también en las clases hoja habría
sido redundante, no incorrecto, pero no es necesario.

## Cómo se descubrió

Como efecto colateral al implementar un backfill de cifrado PII
(`PiiBackfillRunner`): un `ApplicationRunner` sin `@Transactional` que hacía
`jdbc.update()` en un loop, dentro de esta misma app y configuración, ejecutó
el UPDATE 217,400 veces sin que la base de datos cambiara ni una sola vez
(`pii_encryption_status` se mantuvo en `pending: 5178` todo el tiempo). El
backfill se detuvo a tiempo, sin daño a datos reales (ver
`docs/legal/` / commit de PII encryption para el detalle).

## Cómo se confirmó (con evidencia directa, no solo inferencia)

Se escribió una prueba temporal (`TransactionalBugReproTest`, borrada tras
usarse — no forma parte de la suite permanente) que:
1. Insertaba una fila desechable en `ades_personas` (`nombre='ZZZ_TEST_TX_BUG'`,
   con `COMMIT` explícito, fuera del camino sospechoso).
2. Llamaba `PersonaUpdateHelper.actualizarBasico(id, {"estado_civil":"CASADO"})`
   — el método real que usa el PATCH de profesores.
3. Verificaba con una conexión nueva si el cambio persistió.

**Resultado antes del fix:** `estado_civil` seguía en `SOLTERO` (sin cambio) —
bug confirmado con el código real, contra la base de datos real, sin tocar
ningún alumno/profesor existente.

## Cómo se corrigió y se verificó el fix

Se agregó `@Transactional` (import `org.springframework.transaction.annotation.Transactional`)
a los métodos de escritura de los 4 `ApplicationService` listados arriba.

Se verificó con una segunda prueba temporal (`TransactionalFixVerificationTest`,
también borrada) que, a través del **contexto real de Spring** (`@SpringBootTest`,
proxy AOP real — no instanciación manual como en la prueba de reproducción),
un bean `@Transactional` que llama a `PersonaUpdateHelper.actualizarBasico`
ahora sí persiste el cambio. Resultado: `estado_civil` = `CASADO` ✅.

**Suite completa:** 550 tests, 0 fallas relacionadas (2 fallas + 1 error en
`HorarioDomainTest`, módulo de horarios, confirmado sin relación con este
cambio — falla preexistente de validación de `dia_semana`, no tocada hoy).

**Desplegado:** imagen `ades-bff` reconstruida y contenedor recreado, healthy.

## Efecto sobre trabajo previo de esta sesión

- El dual-write de cifrado PII agregado a `PersonaUpdateHelper` /
  `PersonalAdminPersistenceAdapter` (commit `0209ea0`) ahora sí persistirá
  cuando se invoque a través de `AlumnoApplicationService`/`ProfesorApplicationService`/
  `PersonalAdminApplicationService` (que ya tienen `@Transactional`). El
  `PiiBackfillRunner` standalone (`ApplicationRunner`, fuera de un bean con
  `@Transactional`) **sigue teniendo el mismo problema si se ejecuta tal
  cual** — no se corrigió en este pase porque no se autorizó correr el
  backfill sobre datos reales hoy. Antes de activar `PII_BACKFILL_RUN=true`
  de nuevo, hay que envolver su lógica de escritura en un método
  `@Transactional` de un bean real (no se puede anotar `ApplicationRunner.run()`
  directamente por auto-invocación — necesita delegar a un bean inyectado).

## Actualización 2026-07-11 (misma sesión, continuación): barrido completo del proyecto

Tras el fix inicial (4 módulos) se hizo un barrido exhaustivo de **todo**
`backend-spring/src/main/java` (vía agente de exploración + verificación
manual con grep por archivo) buscando el mismo patrón: escritura cruda
(`jdbc.update`/`jdbc.batchUpdate`) sin `@Transactional` en ningún punto de su
cadena de invocación. **El bug era mucho más extendido de lo que parecía
inicialmente — se encontró y corrigió en ~50 archivos adicionales.**

### Hallazgos especiales de alto impacto (no el patrón simple de 3 saltos)

1. **`AuditHttpFilter`** — el log de auditoría de **TODA** mutación HTTP
   (POST/PUT/PATCH/DELETE) nunca persistía. Verificado directamente:
   `SELECT COUNT(*) FROM ades_audit_log` = **0 filas, siempre**, pese a que
   `CLAUDE.md` exige explícitamente "Endpoints mutantes: siempre pasan por
   AuditMiddleware". Es un `OncePerRequestFilter`, no un bean de servicio
   normal — el fix correcto NO es envolver el filtro completo (mezclaría la
   auditoría con la transacción de negocio, perdiendo el registro de un 4xx
   si esa transacción hace rollback), sino extraer el INSERT a un bean nuevo
   (`AuditLogWriter`) con `@Transactional(propagation = REQUIRES_NEW)` —
   transacción propia e independiente, se confirma pase lo que pase con la
   request.
2. **`ProcesosWriteService`** — solo 2 de 14 métodos públicos tenían
   `@Transactional` (`registrarSolicitudSEP`, `registrarSolicitudManual`).
   Los otros 12 —incluyendo **`registrarBaja`** y **`reactivarEstudiante`**
   (dan de baja/reactivan alumnos), `actualizarResolucion` (aprobar/rechazar
   admisión), `inscribirOptativa`/`darBajaOptativa`, `registrarAcuerdo`
   (firma de convivencia), `crearEventoCalendario`, `crearPeriodoEvaluacion`,
   `cerrarPeriodo`, `registrarEvaluacionDiagnostica`, `enqueueNotificacion`,
   `marcarNotificado`— no persistían. Corregidos los 12.
3. **`AdminController`/`AdminWriteService`/`AdminQueryService`** — alta de
   usuarios del sistema (`insertPersona`+`insertUsuario`), desactivación de
   ciclos, edición de menús y permisos por rol: sin protección. Corregido.
4. **`HorarioAscService.importarXml`** — el javadoc decía explícitamente "no
   es @Transactional a propósito, cada card se inserta con autocommit
   independiente... para que un renglón inválido no aborte la transacción".
   Premisa incorrecta en este proyecto (autocommit=false global). El diseño
   de independencia por renglón se preserva correctamente porque cada
   `crear()` (vía `HorarioApplicationService`, ya corregido) abre su propia
   transacción — solo faltaba el `UPDATE` de desactivación suelto en la
   misma clase, extraído a un bean nuevo `HorarioDesactivadorService`.
5. **3 casos de protección parcial** (un método de la clase SÍ tenía
   `@Transactional`, otro método hermano NO): `DisponibilidadDocenteController.eliminar`
   (vs. `.guardar`, que sí estaba protegido), `PlanMejoraService.actualizarEstado`
   (vs. `.generar`), `AjusteDinamicoService.guardarNarrativa` cuando se invoca
   directamente desde el controller (vs. su uso interno dentro de `.ajustar`,
   que sí estaba cubierto). Los 3 corregidos.

### Patrón simple (Controller → ApplicationService → PersistenceAdapter), 23 módulos

Mismo fix mecánico que los 4 originales — `@Transactional` en los métodos
públicos de escritura del `ApplicationService`: asistencia_personal, badges,
cierre (`CierreApplicationService`, distinto de `CierreCicloService` que ya
estaba bien — el `@Transactional` se puso en `CierrePersistenceAdapter` en
vez del ApplicationService para no chocar con el `isolation=SERIALIZABLE`
de `CierreCicloService` cuando ambos se invocan juntos), compliance,
conducta, encuestas, entregas, esquemas_ponderacion, eval_docente,
evaluaciones, expediente, expediente_laboral, horarios, justificaciones,
learning_paths, medico (personal salud + salud avanzada), movilidad,
notificaciones, planes_estudio, portal_familias, procesos (preinscripción),
reinscripcion.

### WriteServices "planos" (sin capa hexagonal), 10 archivos

calendario, direcciones, expediente (documentos + `ExpedienteQueryService.obtenerOCrearExpediente`
fetch-or-create), gradebook/actividades, planes_estudio (alt/NEE),
planteles/clave, portal (admin/publico/usuario — registro, login, ARCO,
postulaciones, documentos; `enviarPostulacion` se envolvió a nivel Controller
para que `marcarPostulacionEnviada` + `incrementarCupo` sean atómicos entre sí).

### Controllers que escriben JDBC directo, 4 archivos

`AulaController` (franjas de disponibilidad), `BienestarController`
(eventos), `CalificacionesController.guardarCualitativa` (UPSERT NEM),
`ProcesosEscolaresController.aprobarEInscribir` (aprobar admisión + crear
alumno + inscripción + usuarios ALUMNO/PADRE_FAMILIA — se envolvió el
método completo del controller para que los 2 `try/catch` de creación de
usuario, que ya degradan con gracia si fallan, ahora también persistan
cuando tienen éxito).

### WebhookService (menor severidad — solo logs de auditoría de webhooks)

`dispatchWebhook` es `@Async` y despacha a N endpoints externos por HTTP en
un loop. Deliberadamente **no** se envolvió el método completo en
`@Transactional` (mantendría una conexión del pool — tamaño 10 — abierta
durante I/O de red lento, riesgo de agotar el pool). Se extrajo el INSERT
del log a un bean nuevo `WebhookLogWriter` con su propio `@Transactional`,
igual patrón que `AuditLogWriter`.

### Verificación del barrido completo

- Compilación: 656 archivos fuente, 0 errores.
- Suite completa: 550 tests, mismas 2 fallas + 1 error preexistentes de
  `HorarioDomainTest` (sin relación, confirmado), 0 fallas nuevas.
- Cada uno de los ~50 archivos modificados se verificó individualmente con
  `git diff` para confirmar que el único contenido agregado es
  `@Transactional`/imports/las extracciones a bean nuevo — sin arrastrar
  cambios ajenos de otros refactors en curso en el repo.
- Desplegado: imagen `ades-bff` reconstruida y contenedor recreado, healthy.

## Actualización 2026-07-11 (misma sesión, continuación): PiiBackfillRunner corregido

Se corrigió el último pendiente. Causa exacta: `PiiBackfillRunner.run()`
llamaba a `procesarLote()`, un método **privado dentro de la misma clase**
(auto-invocación `this.procesarLote()`) — el proxy AOP de Spring no
intercepta llamadas así, por lo que un `@Transactional` puesto ahí nunca se
habría activado (idéntico al bug original de `PersonaUpdateHelper`, solo que
aquí el propio *runner* era su propio problema).

Fix: se extrajo `procesarLote()` a un bean nuevo, `PiiBackfillBatchProcessor`
(`@Service`, método `procesarLote()` con `@Transactional`), inyectado en
`PiiBackfillRunner` en vez de auto-invocado. Cada lote de 200 filas queda
como su propia transacción independiente (no todo el backfill en una sola
transacción gigante).

**No se re-ejecutó el backfill real** para verificar con datos en vivo: un
intento de prueba habría procesado 200 filas reales de `ades_personas` (las
5,178 `pending` siguen ahí, sin tocar), lo cual requiere autorización
explícita como la vez anterior — no implícita en "corrige esto". Verificado
en cambio por: (a) compilación limpia (657 archivos), (b) el mismo patrón
estructural exacto (bean `@Service` separado + `@Transactional` + invocación
vía referencia inyectada, no auto-invocación) que ya se demostró
empíricamente funcional hoy mismo con `AlumnoApplicationService`/
`ContactosApplicationService` (`TransactionalFixVerificationTest`, sección
anterior). Desplegado: `ades-bff` reconstruido, healthy, `PII_BACKFILL_RUN`
sigue en `false` (no se activó).

**Antes de correr el backfill real sobre las 5,178 filas pendientes**: pedir
autorización explícita (mismo patrón que la vez anterior — backup de BD
primero), luego `PII_BACKFILL_RUN=true` en un solo `docker compose up -d
ades-bff`, verificar conteos, y devolver el flag a `false`.

## Actualización 2026-07-11 (misma sesión, continuación): backfill real ejecutado — ✅ completado

Con autorización explícita del usuario (confirmación directa vía pregunta
dedicada, no implícita), se ejecutó el backfill real:

1. Backup fresco (`backups/pre_pii_backfill_real_20260711_200823.sql`, 144MB,
   verificado con datos de `ades_personas` presentes).
2. Línea base confirmada: `5178 pending, 0 completado`.
3. `PII_BACKFILL_RUN=true` en un solo `docker compose up -d ades-bff`, log
   vigilado en vivo desde el primer segundo. Comportamiento correcto esta
   vez: progreso acotado por lotes de 200 (200, 400, 600 ... 5000), lote
   final de 178, y **"Completado. Total de filas procesadas: 5178"** — sin
   loop infinito, converge exactamente al número real de filas pendientes.
4. Verificado en BD: `5178 completado, 0 pending`.
5. **Round-trip de descifrado verificado** en una fila real: se comparó
   (dentro del contenedor, sin imprimir ningún valor de PII en la
   transcripción — solo booleanos de resultado) el CURP y teléfono
   descifrados contra el valor en texto plano de la misma fila usando
   `PiiEncryptionService` real. Resultado: `decrypt_match=true`,
   `hash_match=true` para ambos campos.
6. `PII_BACKFILL_RUN` devuelto a `false`, `ades-bff` redesplegado sin el
   flag, confirmado que no se re-ejecutó (idempotente: aunque hubiera
   quedado en `true`, no habría nada más que procesar).

**Estado final: las 5,178 personas en `ades_personas` tienen CURP, teléfono
y email personal cifrados en reposo (AES-256-GCM) además del texto plano
existente** (retirar el texto plano queda fuera de alcance — ver nota en la
sección de resumen sobre por qué eso es una migración de mayor tamaño).

## Pendiente / recomendación de seguimiento
- El barrido fue guiado por un agente de exploración + verificación manual,
  no por un análisis estático exhaustivo automatizado. Es razonablemente
  completo pero no matemáticamente garantizado al 100%. Recomendación para
  el futuro: un check de CI/pre-commit que falle si aparece `jdbc.update`/
  `jdbc.batchUpdate` en un método sin `@Transactional` en la clase (heurística
  simple, no reemplaza el análisis de cadena de invocación, pero atraparía
  regresiones obvias).
- Los tests unitarios de dominio existentes (`*DomainTest`) no cubren este
  tipo de bug por diseño (no tocan JdbcTemplate real). No se agregó
  infraestructura de test de integración permanente en este pase — las
  pruebas usadas para confirmar y verificar el fix fueron temporales y se
  borraron tras usarse (ver secciones anteriores). Sería valioso, en una
  sesión futura, establecer un patrón de test de integración liviano
  (similar a `TransactionalFixVerificationTest`) que quede en la suite.
