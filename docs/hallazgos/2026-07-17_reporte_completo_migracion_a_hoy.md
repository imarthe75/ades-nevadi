# Reporte completo — de la migración de servidor (2026-07-10) a hoy (2026-07-17)

**Encargo:** consolidar todo lo hecho desde la migración a `ades.setag.mx` hasta hoy, y dar
una lista clara de qué queda pendiente — cruzando `.agent/STATE.md`, los 8 documentos de
`docs/hallazgos/` generados en ese rango, y el trabajo de la sesión actual (R-18 a R-26).

---

## 1. La migración (2026-07-10)

`163.192.138.130` (ades.setag.mx) reemplazó al servidor anterior (`129.213.35.140`), con
recursos reducidos (4 cores/24GB → 2 cores/12GB, ~-45% memoria). Varios servicios no críticos
se desactivaron temporalmente en el bootstrap (Vault, Carbone, ntfy, Paperless, H5P) —
**todos fueron reactivados** en sesiones posteriores (detalle abajo).

---

## 2. Qué se hizo — cronológico

### 07-12 — Auditoría post-migración
Backups automáticos rotos (ruta y nombre de servicio inexistentes) → corregidos + cron
reinstalado. OnDestroy: la medición real (7/79) contradecía un commit previo que declaraba
"implementado" — corregido a 79/79 con el grep correcto (`-lE "implements.*OnDestroy"`).

### 07-13 (+ 5 continuaciones) — Auditoría profunda de horarios/gradebook/ciclo/Vault/RBAC
- `ades_profesores` en 0 filas (seed nunca corrido) → 105 profesores + 864 asignaciones.
- Cadena de inscripción/reinscripción encontrada 100% rota → reconstruida.
- Vault activado end-to-end (bug de permisos de directorio).
- `AsistenciaController` sin verificación de `nivelAcceso` → cualquier autenticado podía
  registrar asistencia de cualquier grupo — corregido.
- `StatsController` daba 403 a **todo** usuario (claim JWT inexistente) → dashboard de
  dirección estaba 100% inutilizable — corregido.
- `MINIO_ENDPOINT=localhost:9000` — archivos nunca funcionaron vía SeaweedFS — corregido.
- Solver de horarios: NPE corregido, corrida real de 408 horarios con 0 violaciones duras.
- Reinscripción masiva **investigada y decidida NO ejecutar** (habría corrompido 2,028
  inscripciones reales del ciclo vigente — decisión correcta, no un pendiente).
- Superset: 4 bugs de raíz (contraseña desalineada, columnas inexistentes en 5/7 charts,
  vista fantasma, hostname interno en el embed) → BI funcional end-to-end.
- Migración MinIO/SeaweedFS → Oracle Object Storage completada.
- H5P y Paperless-ngx reactivados y verificados.

### 07-15 — Remediación R-1 a R-17 (plan de seguridad)
Ledger de auditoría endurecido a SHA-256 encadenado real; CSP en nginx; imágenes pineadas por
digest; BOLA/BFLA corregido en varios controllers (Biblioteca, EvalDocente, otros).

### 07-16 — Plan BOLA/BFLA (Día 1-5) + auditoría de huecos no revisados + remediación de 25 hallazgos
- 15 controllers con hueco real de scoping por plantel corregidos (7 de ellos solo aparecieron
  tras ampliar el grep a variantes de nombre).
- HikariCP 10→25 conexiones (saturación real confirmada en logs).
- `GlobalExceptionHandler` ahora mapea `NoResourceFoundException`→404 (antes 500).
- Una segunda pasada de auditoría encontró que la "cola larga" BOLA/BFLA **no** estaba
  cerrada: 14 controllers Spring adicionales + FastAPI completo sin auditar + Grafana sin
  autenticación — remediados en la sesión siguiente (mismo día, continuación):
  14 controllers Spring + 4 endpoints FastAPI corregidos, Grafana con `auth_basic`, JWT
  valida `aud`, `xlsx` movido al CDN oficial (2 CVE HIGH sin fix en npm), JaCoCo/vitest
  coverage instalados, migración 150, gate `security-audit.yml` real en CI.

### 07-17 (sesión de hoy) — 3 bloques de trabajo

**Bloque A — Limpieza de datos + accesibilidad + despliegue** (antes de esta conversación):
2,197 filas huérfanas eliminadas (13 tablas), 13 materias remapeadas contra el plan NEM
oficial SEP, `quill` eliminado (CVE, dependencia muerta), backup a Oracle Object Storage con
prueba de restore real verificada (190 tablas, 2,031 alumnos), ARIA 1.3%→86% (68/79
componentes), fix de UX en alta de alumno (CURP inválido ya no bloquea el clic). Desplegado a
producción (`ades-frontend` reconstruido).

**Bloque B — Esta conversación, R-18 a R-22** (código en disco, commiteado por el usuario a
mitad de sesión — commits `b5a5d84` y `46883ca`):
- **R-19** (feedback visual en mutaciones): 24 componentes corregidos con el patrón
  signal-de-loading wireado al botón real (verificado mapeando método↔botón, no con grep de
  conteo — la técnica quedó documentada como skill reutilizable).
- **R-20** (validación estructural en datos sensibles): `AdesValidators` extendido con
  variantes imperativas (`curpValido`/`rfcValido`/`nssValido`/`telefonoValido`) — pasó de
  usarse en 1/79 componentes a 9. De paso, un bug real de persistencia: `alumno-perfil` dejaba
  editar el CURP pero nunca lo enviaba al guardar.
- **R-22**: `bbb.component.ts` con `window.confirm()` residual → `ConfirmationService`;
  `capacitaciones.component.ts::validar()` investigado y — no era código muerto — resultó ser
  una función de RH a medio terminar (backend completo, sin botón); se conectó con control de
  acceso `nivelAcceso ≤ 3`.

**Bloque C — R-21, R-23/24/25/26** (código en disco, **sin commitear todavía**):
- **R-21**: muestreo real con navegador (Playwright, JWT real de Authentik, 12 páginas
  navegadas contra el mismo build que sirve producción). Corrigió una imprecisión del propio
  método de auditoría: el grep de "breadcrumb" decía 2.5% de cobertura; la evidencia real de
  navegador mostró 12/12 páginas con breadcrumb funcional (vive en el shell compartido, no en
  cada componente — el grep por-archivo nunca lo iba a ver). 4 hallazgos reales encontrados.
- **R-23**: `horarios.component.ts` exponía "Timefold Solver"/"Análisis del Motor (Timefold)"
  al usuario final (nombre de la librería interna, no término de dominio) — corregido.
- **R-24**: bug de raíz en `interactive-grid.component.ts` — `ColumnConfig.type: 'date'`
  estaba declarado en la interfaz pero nunca implementado en el `<td>`, así que cualquier
  columna de fecha se mostraba como timestamp ISO crudo. Corregido en el componente
  compartido + barrido de los 23 componentes candidatos (9 tenían el bug real, 14 ya estaban
  bien con un campo pre-formateado).
- **R-25**: `/calificaciones` y `/gradebook` compartían el mismo texto de breadcrumb
  ("Calificaciones"), indistinguibles — corregido.
- **R-26** (encontrado al investigar R-24): `ColumnConfig.template` tampoco estaba
  implementado — la columna "Acciones" completa de `portal-admin.component.ts`
  (editar/publicar/ver postulaciones/archivar convocatorias) era invisible. Al arreglarlo
  apareció un segundo bug independiente: `(rowSelect)` en vez de `(rowSelected)` — typo
  aislado que dejaba **todo el módulo de convocatorias sin reaccionar a clics**, sin ningún
  error visible. Ambos corregidos y verificados con `ng build --configuration production`
  real (no solo `tsc --noEmit`, que no detecta errores de binding de plantilla — lección
  documentada para la próxima vez).

Toda la sesión de hoy quedó documentada en
`docs/hallazgos/2026-07-16_plan_revision_heuristicas_cognitivas.md` y en el nuevo skill
reutilizable `.agent/skills/frontend-heuristicas-audit/SKILL.md`.

---

## 3. Verificación en vivo hecha para este reporte (no solo lectura de docs)

- `auditoria.reporte_cobertura()`: `ades_log_autenticacion` ya tiene `tiene_biu = true` — el
  hueco mencionado en `2026-07-15_validacion_remediacion.md` **está cerrado** (vía migración
  150, confirmado en vivo, no solo por lo que dice el documento).
- `docker-compose.yml`: 12 imágenes están pineadas por digest SHA-256 (Vault, ntfy,
  node-exporter, Prometheus, Grafana, Paperless, pgbouncer + 2 exporters, nginx, certbot) —
  bastantes más que la nota "2 imágenes pineadas" del header de `CLAUDE.md` (esa nota se
  refería solo a lo pineado *esa sesión puntual*, no al total acumulado). `ades-h5p:latest`
  **sigue sin pinear** — único gap real confirmado en este punto.
- `git status --short`: **16 archivos modificados sin commit** ahora mismo (todo el Bloque C
  de hoy: R-21/23/24/25/26) — la cifra de "~70 archivos" que menciona `STATE.md`/`CLAUDE.md`
  es de *antes* de los commits que hiciste a mitad de esta sesión; ya se redujo bastante.
- `docker images`: el contenedor `ades-frontend` corriendo fue construido antes del Bloque C
  de hoy — **el código de R-21/23/24/25/26 no está desplegado todavía**, solo en disco.

---

## 4. Qué queda pendiente — categorizado

### 4.1 Acción tuya requerida ahora mismo (decisión, no código)
- **Commitear el Bloque C** (16 archivos: R-21/23/24/25/26) — por Regla Mandatoria #21 no
  comiteo sin que lo pidas explícitamente. Dímelo y lo hago.
- **Reconstruir y desplegar `ades-frontend`** — el fix de R-26 (módulo de convocatorias
  completamente muerto) y los demás de hoy no están en vivo hasta el rebuild.

### 4.2 Código listo para atacar si quieres que siga (no requieren tu input de negocio)
- Extraer el patrón `requireAccesoGrupo`/`requireAccesoClase` a un helper compartido en
  `AdesUserService` — evita que el mismo bug BOLA/BFLA se reintroduzca por copy-paste (ya
  pasó una vez: 7 controllers con la misma falla replicada).
- `ades-h5p:latest` sin pinear por digest (bajo riesgo, 1 línea).
- Asociación `for`/`id` completa en 272 `<input>` nativos con label visual pero sin
  verificación programática (accesibilidad — mecánico pero grande, mejor como pase dedicado).
- Conectar las suites `06-edge-cases.spec.ts` (20 tests) y `paginacion-tareas.spec.ts` (27
  tests) a autenticación OIDC real — hoy son scaffold sin ejecutar, así que los fixes BOLA/BFLA
  de esta semana no tienen red de regresión automatizada.
- Investigar los 6 fallos de `19-cascadas-grupos.spec.ts` y el timeout de `12-certificados.spec.ts`
  (`CER-E2E-10`).
- `undici` (transitivo de `@angular/build`) — CVE sin parche no-breaking disponible todavía;
  revisar si ya existe uno.
- Migrar `/chatbot/sql` de mitigación por prompt-hint + blacklist a RLS nativo de Postgres —
  proyecto de mayor alcance, no un fix de una sesión.
- Rate limiting FastAPI: solo 4/18 routers con límite explícito (`chatbot.py`/`ai_assistant.py`
  sin límite dedicado pese a ser los más sensibles a abuso de costo de IA).
- API6/API7/API9/API10 del OWASP API Security Top 10 — nunca evaluados en ninguna ronda
  (Unrestricted Business Flows, SSRF, Inventory Management, Unsafe API Consumption).

### 4.3 Requieren tu decisión de negocio (no son bugs de código)
- **Preparatoria Metepec:** el plan curricular pide ~81h/semana pero solo hay 35 franjas
  definidas — el solver da -872 violaciones duras. ¿Falta definir jornada extendida, o el plan
  está sobredimensionado? Sin esta decisión el horario de esa preparatoria no puede generarse.
- Nómina real de personal: 91 nombres de profesores siguen siendo placeholder generado (solo
  14 de Ixtapan Secundaria son reales) — se necesita la nómina real del Instituto.
- Reinscripción masiva sigue bloqueada hasta que exista el ciclo 2027-2028 en el sistema.
- Confirmar con dirección Nevadi que la escala 0-10 de Preparatoria es definitiva.
- Aviso de Privacidad LFPDPPP — el borrador sigue pendiente de revisión legal real (no
  bloqueante para operar, pero sí para el cumplimiento formal).
- Plantilla DOCX de credencial de alumno — diseño gráfico, no tarea de código.
- Decidir si exponer la UI de Paperless-ngx vía nginx para el personal administrativo.
- Validar visualmente en navegador real el iframe de Superset embebido (solo se probó por API).
- Probar el flujo completo de subida de archivo real vía UI contra Oracle Object Storage.
- Export CSV de Superset devuelve ZIP vacío en charts sin `query_context` guardado.

### 4.4 Diferido a propósito (política ya decidida, no pendiente real)
- `audit_aiud` (ledger completo, 180 tablas en `PENDIENTE_AIUD`) y `ENVIRONMENT=production` —
  ambos diferidos al go-live por decisión explícita ya documentada, correctos así hoy.
- Prueba de restore simulando *pérdida total del servidor* (la del 07-17 sí descargó de Oracle
  real, pero no simuló "el servidor ya no existe") — deliberadamente fuera de alcance hasta que
  se planee un ejercicio de DR formal.

---

## 5. Balance

Desde la migración (07-10) hasta hoy: **8 rondas de auditoría de seguridad/calidad**
(post-migración, horarios/RBAC, BOLA/BFLA día 1-5, huecos no revisados, 25 hallazgos, y esta
sesión de heurísticas UX en 3 rondas R-18→R-26), con **decenas de bugs reales encontrados y
corregidos** — varios de severidad alta (StatsController 403 universal, AsistenciaController
sin control de acceso, MinIO endpoint roto, portal-admin completamente muerto). El patrón
constante en todas las rondas: **medir con grep/script objetivo primero, no confiar en lo que
un commit anterior "declaraba" completado** — varias veces la medición real contradijo lo
documentado (OnDestroy 7/79 vs "implementado", breadcrumb 2.5% vs 100% real, `template`/`type`
declarados pero nunca implementados).

Lo que queda pendiente es mayoritariamente **decisiones de negocio** (nómina real, horas de
Preparatoria, revisión legal) o **trabajo grande ya identificado y acotado** (E2E sin conectar
a auth real, 272 inputs de accesibilidad, RLS del chatbot) — no hay, hasta donde se auditó,
ningún hueco de seguridad crítico sin al menos un plan explícito.
