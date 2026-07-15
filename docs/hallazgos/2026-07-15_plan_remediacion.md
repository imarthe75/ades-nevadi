# Plan de remediación ADES — 2026-07-15

**Contexto:** decisión del 2026-07-15 — hay un **único servidor** (ades.setag.mx,
163.192.138.130) que **es producción a nivel de infraestructura** (TLS público, datos reales
cargados). No hay entorno separado. **Matiz importante (2026-07-15):** el *sistema* aún está
en **etapa de desarrollo / pre-liberación** — todavía no se ha liberado a los usuarios finales
del Instituto. Por eso las acciones que solo tienen sentido "en operación real" (activar el log
de auditoría completo `audit_aiud`, pasar `ENVIRONMENT` a `production`) se **difieren al
go-live**, no se hacen hoy. Este plan corrige lo pendiente detectado en el análisis de
entregabilidad (`docs/hallazgos/2026-07-15_analisis_honesto_entregabilidad.md`), con el estado
**verificado en vivo** contra código y base de datos (no contra documentos).

**Correcciones de la verificación en vivo respecto al análisis preliminar:**
- ✅ **Backups NO están rotos.** El script `scripts/backup-ades.sh` fue reescrito el 07-12,
  el cron `0 2 * * *` está activo y hay un backup completo de hoy (2026-07-15 02:00) con
  dump de PostgreSQL de 33M + Valkey + config + volúmenes, retención 7 diarios / 4 semanales
  e instrucciones de restore en el manifest. Lo pendiente aquí es **hardening** (copia fuera
  del servidor + prueba de restore), no reparación.
- ✅ De las 6 tablas sin auditoría completa, **solo `ades_suplencias` es tabla de negocio
  real**; las otras 5 (`ades_audit_log`, `ades_encryption_audit`, `ades_mv_refresh_log`,
  `ades_log_autenticacion`, `ades_pii_encryption_backup_20260619`) son logs/tablas de sistema
  o un backup temporal.

---

## Resumen por prioridad

| ID | Acción | Prioridad | Esfuerzo | Riesgo si no se hace |
|---|---|---|---|---|
| R-1 | **Endurecer el ledger de auditoría** — ✅ **COMPLETADO 2026-07-15** (SHA-256, encadenamiento real, `fn_verificar_cadena`, `fn_reconciliar_tabla`, 3 triggers apagados, log truncado). Activación de `audit_aiud` **diferida al go-live** | ✅ Hecho / 🟠 go-live la activación | — | — |
| R-2 | `ENVIRONMENT=development` → `production` — **diferido al go-live** | 🟠 go-live | Bajo-medio | Antes de liberar, el sistema debe correr en modo producción (HTTPSRedirect, rate limiting) |
| R-3 | Backup off-server + prueba de restore documentada | 🟠 P1 (backups en sí ya funcionan) | Bajo | Punto único de fallo: backup en el mismo disco del único servidor |
| R-4 | `ades_suplencias` columnas de auditoría; evaluar drop de `ades_pii_encryption_backup_20260619` | ✅ **COMPLETADO 2026-07-15** (fix limpio de entidad, no solo hotfix) | — | — |
| R-5 | Auditoría BOLA/BFLA de **los 82 controllers** (no solo 47) | ✅ **COMPLETADO 2026-07-15** — auditados los 82, ~20 gaps reales corregidos (ver detalle abajo), 555/555 tests, BUILD SUCCESS, contenedor estable | — | — |
| R-6 | CSP en todos los vhosts nginx | ✅ **COMPLETADO 2026-07-15** (7/8 con CSP; el 1 restante es redirect HTTP→HTTPS sin contenido, no lo requiere) | — | — |
| R-7 | Pinear imágenes Docker `:latest` | ✅ **COMPLETADO 2026-07-15** (9 restantes: 8 deshabilitadas sin riesgo, 1 build local sin upstream externo) | — | — |
| R-8 | `SECURITY.md` + política de reporte de vulnerabilidades | 🟡 P2 | Bajo | Sin canal de reporte responsable |
| R-9 | QA manual Gradebook Preparatoria (cierre 0.3) | 🟠 P1 | Bajo | Confirmar en UI que el fix de acreditación funciona |
| R-10 | Correr 21 specs E2E por rol; ampliar CI de 5→21 | 🟠 P1 | Medio | Regresiones no detectadas antes de tocar prod |
| R-11 | Integrar `check-api-contracts.js` a CI (Fase 6) | 🟡 P2 | Bajo | Reaparición de bugs de contrato camelCase/snake_case |
| R-12 | `@Transactional` en casos de uso multi-escritura | 🟡 P2 | Bajo | Escrituras parciales (mismo patrón del bug de personas 07-11) |
| R-13 | Adopción de tipos OpenAPI generados (hoy 0 componentes) | 🟢 P3 | Alto | Deuda estructural — elimina la familia entera de bugs de contrato |
| R-14 | Accesibilidad ARIA (hoy ~1.3% componentes) | 🟢 P3 | Alto | No cumple usabilidad; barrera para usuarios con discapacidad |
| R-15 | Cobertura de tests medida (JaCoCo + Vitest coverage) | 🟢 P3 | Medio | La métrica de calidad simplemente no se mide |
| R-16 | Confirmar escala 0-10 Prepa con dirección Nevadi (0.5) | 🟢 P3 | Nulo | Evitar que una auditoría futura reproponga 0-100 |
| R-17 | Actualizar `STATE.md` (congelado desde 07-06) + rito de cierre | 🟡 P2 | Bajo | Pérdida de contexto entre sesiones |

---

## P0 — Endurecer el ledger de auditoría ahora (en dev), activarlo en el go-live

### R-1 — Endurecer el ledger de auditoría (hacer YA) + activar en go-live (diferido)
**Decisión 2026-07-15:** como el sistema aún está en desarrollo, NO se activa `audit_aiud`
todavía. En su lugar se robustece el mecanismo mientras no hay tráfico real, para que llegue
"blindado" al go-live. El estado actual del ledger es débil: **MD5** (roto criptográficamente),
`hash_nuevo` calculado **solo sobre los datos de la fila** (no incorpora el hash anterior, así que
no hay encadenamiento real en cascada), encadenamiento **por registro** (`uuid_ref`+tabla) en vez
de cadena global, y **sin función de verificación**. Hoy: `auditoria.reporte_cobertura()` → 176
`PENDIENTE_AIUD`, 3 `COMPLETO`, y solo **39 filas** de prueba en `auditoria.log_auditoria`.

**Trabajo de endurecimiento (ahora, en dev — orden de ejecución):**
1. **Apagar los 3 `audit_aiud` activos** — están encendidos sobre datos de prueba sin valor;
   quitarlos deja el mecanismo limpio para migrarlo. (`audit_biu` se conserva: es el que
   gestiona ref/row_version/timestamps y no es opcional.)
2. **Limpiar `auditoria.log_auditoria`** — `TRUNCATE`; las 39 filas son de prueba con hashes MD5
   viejos que no queremos arrastrar a la cadena nueva (una cadena que empieza con basura pierde
   sentido). Backup previo de hoy ya existe (2026-07-15 02:00) como red de seguridad.
3. **Migrar `fn_auditoria_aiud` a un ledger robusto** (pgcrypto ya está instalado):
   - **MD5 → SHA-256** (`encode(digest(..., 'sha256'), 'hex')`).
   - **Encadenamiento real:** `hash_nuevo = sha256(hash_original ‖ datos_fila)` para que alterar
     un registro rompa en cascada todos los hashes posteriores (hoy no ocurre).
   - **Función de verificación** `auditoria.fn_verificar_cadena(tabla, uuid_ref)` que recorre la
     cadena y devuelve el primer registro alterado (hoy no existe ninguna).
4. **Dejar todo listo pero apagado** — con una función `auditoria.activar_produccion()` (o
   simplemente el runbook con los `asignar_triggers()`) para ejecutar en el go-live.

**Activación (diferida al go-live, cuando el sistema se libere a usuarios reales):**
- Por cada tabla con PII/negocio: `SELECT auditoria.asignar_triggers('public.ades_<tabla>');`
  Empezar por: `ades_personas`, `ades_contactos_familiares`, `ades_expediente_medico`/salud,
  `ades_calificaciones_periodo`, `ades_conducta*`, `ades_sanciones*`, reinscripción, admisión.
- Medir impacto (una fila por mutación) en las 2 tablas más calientes antes de activarlo en todas.
- **Verificar:** `SELECT * FROM auditoria.reporte_cobertura();` — las tablas objetivo → `COMPLETO`.

**Fuera de alcance de R-1 (decisión aparte, según threat model):** el *anclaje externo* de la
cabeza de la cadena (LACChain / firma ed25519, ver `.agent/LACCHAIN_ACTIVACION.md` y ADR-0004).
Solo necesario si el modelo de amenaza incluye a un operador/DBA malicioso que pueda recalcular
toda la cadena; para manipulación por usuarios de la app, los puntos 1-3 ya bastan.

### R-2 — `ENVIRONMENT` a `production` (diferido al go-live)
Hoy `.env` y `docker-compose.yml` usan `ENVIRONMENT=development`, lo cual es **correcto mientras
el sistema esté en desarrollo**. En el go-live cambiar a `production`, con efectos en cascada que
hay que probar (**no flip a ciegas**):
- HTTPSRedirectMiddleware / comportamiento de HSTS.
- Rate limiting (perfiles pueden diferir por entorno).
- Cualquier `if ENVIRONMENT == "production"` en el código (FastAPI y Spring) — **grep primero**
  para inventariar los efectos exactos.
- **Cómo:** cambiar el valor, `docker compose up -d` de los servicios afectados, y probar en
  vivo: login OIDC, un guardado de calificación, un endpoint con rate limit. Con backup previo.

### R-3 — Backup fuera del servidor + prueba de restore
Los backups funcionan pero **viven en el mismo disco del único servidor** — si el disco/host
muere, se pierden datos y backup juntos.
- **Cómo:** sincronizar `/opt/ades/backups/` a un destino externo (bucket S3/otro host) tras
  cada corrida (añadir al final de `backup-ades.sh` o un segundo cron). Verificar que el destino
  externo NO comparte fallo con el servidor.
- **Prueba de restore:** al menos una vez, restaurar el dump de hoy en una BD temporal y
  verificar conteos (`ades_personas`, calificaciones) — documentar el resultado. Un backup no
  probado no es un backup.

### R-4 — `ades_suplencias` + limpieza de tabla PII de respaldo
- `ades_suplencias` (tabla de negocio real) no tiene columnas de auditoría → migración que
  añada `ref/row_version/fecha_creacion/fecha_modificacion/usuario_creacion/usuario_modificacion`
  + `SELECT auditoria.asignar_biu('public.ades_suplencias');` (Regla Mandatoria #3-4).
- `ades_pii_encryption_backup_20260619` es una tabla de respaldo con PII creada durante la
  migración de cifrado de junio — evaluar si ya no se necesita y **eliminarla** (minimización
  de datos LFPDPPP); si se conserva, documentar por qué y por cuánto tiempo.

## P1 — Seguridad y QA (antes de ampliar el uso, 4-6 días)

### R-5 — Cerrar los huecos BOLA/BFLA en controllers no auditados — ✅ COMPLETADO 2026-07-15

Se auditaron **los 82 controllers** (no solo los 47 pendientes) contra el checklist de
CLAUDE.md, con 3 agentes en paralelo por lote (~27-29 archivos c/u), incluyendo una segunda
pasada tras un corte por límite de sesión de API a mitad de trabajo. Verificación final:
`mvn test` **555/555 en verde**, `docker compose build` **BUILD SUCCESS**, `ades-bff` estable
(RestartCount=0) tras redeploy.

**Hallazgos más graves corregidos** (BOLA/BFLA reales, no solo defensa en profundidad):
- `BoletasController` — cualquier cuenta autenticada podía descargar la boleta (PII de
  calificaciones) de **cualquier** estudiante por UUID; el batch por grupo tampoco tenía
  restricción de rol.
- `CierreCicloController` — el endpoint legado `POST /cierre-ciclo` ejecutaba el cierre de
  ciclo **irreversible** + promoción masiva sin ninguna verificación de `nivelAcceso`.
- `ProcesosEscolaresController` — `descargarZip`/`listarDocumentos`/`generarCarta`/
  `historialAdmision` exponían CURP y documentos de admisión a cualquier autenticado.
- `PlaneacionController` — 18 endpoints GET (temario, cobertura, alertas de rezago, insights)
  sin `resolveUser()` en absoluto.
- `AulaController.eliminarFranja` — única mutación del controller sin `verificarPlantelDelAula`
  (sus hermanas `update`/`patch`/`agregarFranja` sí la tenían) — BOLA cross-plantel.
- `CalificacionesController` — un docente podía calcular/guardar calificaciones de cualquier
  grupo/materia sin estar asignado.
- `EvalDocenteController`, `BibliotecaController`, `CapacitacionDocenteController`,
  `BadgeController`, `LicenciaPersonalController` — hallazgos originales del 07-15 (evaluador
  suplantable, biblioteca cross-plantel, sin chequeo de rol), confirmados íntegros.
- Además: `BienestarController`, `CertificadosController`, `CondicionCronicaController`,
  `ConductaController` (menor), `ProfesorController` (PII de docente sin scoping),
  `LearningPathsController`, `PlanesEstudioController` (planes NEE), `SupersetController`
  (dashboard key no forzado al rol real).

**Casos dudosos — revisados y cerrados (continuación de sesión, 2026-07-15):**
- `BadgeController.badgesAlumno` — sin verificación de acceso al alumno; agregado
  `requireAccesoAlumno` (mismo criterio que `EntregasController`). `listar`/`detalle` (catálogo
  de badges, no datos de alumno) se dejaron sin cambio — riesgo bajo, diseño razonable.
- `CapacitacionDocenteController` (`listar`/`resumen`/`detalle`) — inconsistente con sus propias
  escrituras (ya restringían nivel 4 a registros propios); ahora los 3 GET aplican la misma regla.
- `DisponibilidadDocenteController` — **peor de lo esperado**: ni siquiera las escrituras
  (`guardar`/`eliminar` disponibilidad de un docente) tenían control de rol. Agregado
  `requireStaff` a los 5 endpoints + restricción de ownership (nivel 4 solo su propia
  disponibilidad) en `guardar`.
- `EvalDocenteController` (criterios GET) y `EvaluacionAvanzadaController.listarEscalas` —
  catálogos de configuración (dimensiones/ponderaciones, escalas cualitativas), no datos de
  alumno — confirmado que no requieren cambio.
- `MedicoController.getPersonalSalud` / `PersonalAdminController.get` — el listado ya scopeaba
  por plantel vía `getEffectivePlantelId`, pero el detalle por id no lo verificaba. Agregado
  chequeo de `plantel_id` post-fetch en ambos.
- `SuplenciaController` (`listarSuplencias`/`sugerirSuplentes`) — agregado `requireStaff`,
  consistente con el `POST` que ya lo exigía.
- `PortalUsuarioController.eliminarDocumento` — confirmado el bug: `fetchEstadoPostulacion`
  usaba `queryForObject` (lanza `EmptyResultDataAccessException` sin capturar → 500) en vez de
  devolver `null`. Corregido a `queryForList` + null-check (el mismo patrón que `subirDocumento`
  ya esperaba pero nunca alcanzaba), y agregado el null-check faltante en `eliminarDocumento`.

Verificación final de todo lo anterior: `BUILD SUCCESS`, **555/555 tests**, `ades-bff` estable
(RestartCount=0) tras redeploy.

Total: 51 archivos modificados en la jornada de R-5 (controllers + query services/application
services asociados donde el fix lo requería).

### R-6/R-7 — Endurecer infraestructura
- CSP presente solo en 2 de ~6 bloques `server` de `infrastructure/nginx/nginx.conf` — añadirla
  a los vhosts que faltan (excepto donde el iframe de Superset/Grafana lo impida, ya documentado).
- Pinear a versión exacta las 11 imágenes en `:latest` de `docker-compose.yml` (vault×2, ntfy,
  prometheus, grafana, node-exporter, postgres-exporter, pgbouncer-exporter, paperless-ngx,
  pgbouncer, certbot).

### R-9/R-10 — QA de los flujos ya corregidos
- QA manual en la UI de Gradebook, vista Preparatoria: alumno con ≥6.0 aparece "acreditado", no
  "en riesgo" (valida mig. 134 en la interfaz).
- Correr los **21 specs** de `frontend/e2e/` con tokens reales por rol y ampliar el workflow
  `e2e-tests.yml` de 5 a 21 specs.

## P2 — Calidad de proceso (1 semana, en paralelo)

- **R-8** `SECURITY.md` con canal de reporte de vulnerabilidades.
- **R-11** Integrar `scripts/check-api-contracts.js --strict` a `.github/workflows/` una vez
  estable (hoy corre pero fuera de CI).
- **R-12** Mover `@Transactional` al caso de uso en servicios multi-escritura
  (`DisponibilidadApplicationService`) en vez del controller.
- **R-17** Actualizar `STATE.md` (congelado en la sesión 07-06) y retomar el rito de cierre por
  sesión que exige el CLAUDE.md.

## P3 — Deuda estructural (post-entrega, planificado)

- **R-13** Adoptar los tipos OpenAPI ya generables (`npm run generate:api-types`) en los
  componentes Angular — hoy la adopción es 0. Es el fix que vuelve *estructuralmente imposible*
  la familia de bugs de contrato de nombres.
- **R-14** Accesibilidad ARIA (hoy ~1.3% de componentes) + gate a11y en CI con
  `@axe-core/playwright` (ya instalado).
- **R-15** Habilitar JaCoCo (backend) y `test:coverage` (Vitest) para medir cobertura real.
- **R-16** Confirmar con dirección Nevadi que la escala 0-10 de Preparatoria es definitiva.

---

## Secuencia recomendada

**✅ Completado 2026-07-15 (esta sesión):** R-1 (endurecimiento), R-4, R-5 (los 82 controllers),
R-6, R-7. Verificado: 555/555 tests, BUILD SUCCESS, `ades-bff` estable.

**Sigue pendiente, corto plazo:**
1. **R-3** backup off-server + prueba de restore (los backups en sí ya funcionan).
2. Cerrar los **casos dudosos** listados en R-5 (endpoints de solo lectura sin `requireX`) y
   `PortalUsuarioController` (500→404).
3. **Calidad de proceso:** R-8 (hecho — `SECURITY.md` existe), R-11 (hecho — en CI), R-12, R-17.
4. **R-9/R-10:** QA manual Gradebook Prepa + correr los 21 specs E2E completos por rol.

**En el go-live (cuando el sistema se libere a usuarios reales):**
5. **R-1 activación:** `asignar_triggers()` sobre las tablas con PII (ledger ya endurecido y
   verificado — SHA-256, encadenamiento real, `fn_reconciliar_tabla`).
   **R-2:** `ENVIRONMENT` → `production` con la prueba en vivo descrita.
6. **Piloto supervisado:** entregar a un plantel/nivel acotado con acompañamiento 2-4 semanas
   antes del rollout completo.

**Continuo:** P3 (deuda estructural: tipos OpenAPI, accesibilidad, cobertura).
