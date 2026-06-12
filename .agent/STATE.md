# 📈 Estado y Bitácora del Agente Residente (STATE.md)

Este documento es el diario de vida y bitácora del agente. Debe ser leído en el **Rito de Inicio** y actualizado en el **Rito de Cierre**.

## 🔄 Rito de Inicio (Bootstrapping)
*Cada vez que inicies sesión o seas llamado, ejecuta estos pasos:*
1. Lee tu leyes en `.agent/AGENT.md`.
2. Lee tu propósito en `.agent/CONTEXT.md`.
3. Revisa la lista de pendientes de la última sesión en la sección **"Próximos Pasos"** de este archivo.
4. Verifica que los servicios de Valkey y Postgres estén saludables.
5. Confirma que el diseño frontend está alineado con el mandato Oracle APEX descrito en `.agent/CONTEXT.md`.

---

## 📅 Bitácora

### 🔑 Estado del Agente:
- **Última Conexión:** 2026-06-04
- **Estado Cognitivo:** Operacional ✅
- **ADRs Registrados:** 0001 (ADR Inicial de Génesis) · 0002 (Heurísticas) · 0003 (UUID PKs)

### 🏗️ Estado de Infraestructura (2026-06-04):

| Servicio           | Estado    | Notas |
|--------------------|-----------|-------|
| PostgreSQL 18      | ✅ healthy | 57 tablas, seeds cargados (54 grupos, 80 profesores, 1620 alumnos, ciclo 2026-2027) |
| Valkey 9.1.0       | ✅ healthy | |
| MinIO              | ✅ healthy | |
| Authentik server   | ✅ healthy | 2026.5.2 · accesible en https://ades.setag.mx/auth/ |
| Authentik worker   | ✅ healthy | |
| nginx              | ✅ running | TLS activo (Let's Encrypt) · bind mount /etc/letsencrypt |
| ades-api           | ✅ healthy   | 175 operaciones REST (FASE 1–10) |
| ades-frontend      | ✅ running   | Angular 22 · ng serve :4200 · ades.setag.mx OK (HTTP 200) |
| superset           | ✅ running   | 6.1.0 · pendiente primer arranque manual |

### 🛠️ Tareas Completadas hoy (2026-06-04):
- [x] Estandarización de PKs: todas las tablas migradas de `BIGINT GENERATED ALWAYS AS IDENTITY` a `UUID NOT NULL DEFAULT gen_random_uuid()` en `001_initial_schema.sql` (DDL de referencia del framework).
- [x] Columnas FK migradas de `BIGINT` a `UUID` en el schema de referencia.
- [x] Referencias polimórficas `entidad_id` migradas de `BIGINT` a `UUID`.
- [x] `SKILL.md` database-liquibase-postgresql actualizado: regla mandatoria UUID, skeleton canónico con UUID, checklist de PR actualizado.
- [x] `.agent/CONTEXT.md` actualizado: convención de PK a UUID, FKs a UUID.
- [x] ADR `DECISIONS/0003-uuid-primary-keys.md` creado y registrado.
- [x] Script idempotente `db/migrations/20260604_0001_ades_nevadi.sql` creado: asegura existencia de todas las PKs y FKs usando DO blocks con verificación en pg_constraint.
- [x] `CONTEXT.md` actualizado: Ixtapan tendrá preparatoria (6 semestres UAEMEX) con `is_active=FALSE` proyectada.
- [x] Reglas de negocio y tabla de planteles actualizadas (Tenancingo prep incorporada, Ixtapan prep proyectada).

### 🚨 Lecciones Aprendidas:
- Los certs Let’s Encrypt deben montarse como bind mount al host (`/etc/letsencrypt:/etc/letsencrypt:ro`), no como volumen Docker nombrado — el volumen queda vacío si el cert fue emitido fuera del ciclo de vida del compose.
- La variable de Authentik es `AUTHENTIK_SECRET_KEY` (guión simple), no `AUTHENTIK_SECRET__KEY`.
- `depends_on` en nginx debe incluir solo servicios que realmente existen y arrancan; agregar services no construidos bloquea el arranque de nginx.
- **PKs UUID:** `BIGINT GENERATED ALWAYS AS IDENTITY` no debe usarse como PK en tablas ADES nuevas. Usar `UUID NOT NULL DEFAULT gen_random_uuid()` (o `uuidv7()` en PG18). Las columnas FK correspondientes también deben ser `UUID`.
- **Grupos inactivos proyectados:** los grados/semestres futuros (Tenancingo prep sem 3-6, Ixtapan prep sem 1-6) se crean con `is_active=FALSE` en los seeds; se activan ciclo a ciclo sin nueva migración DDL.

---

## Sesión 2026-06-10 — FASE 27: Certificación Digital Ed25519 + APEX Library

### 🔑 Estado del Agente:
- **Última Conexión:** 2026-06-10
- **Estado Cognitivo:** Operacional ✅
- **ADRs Registrados:** 0001 (Génesis) · 0002 (Heurísticas) · 0003 (UUID PKs) · 0004 (Firma Digital Ed25519)

### 🛠️ Tareas Completadas hoy (2026-06-10):

**APEX Component Library (continuación):**
- [x] Shell TypeScript errors resueltos: `ToastModule`/`MessageService` eliminados de imports
- [x] 20 feature components migrados de `MessageService` → `ApexNotificationService`
- [x] Menú de navegación estático con 11 secciones filtradas por `nivelAcceso()`
- [x] `apex-toast-container` único en ShellComponent

**FASE 27 — Certificación Digital Ed25519:**
- [x] `db/migrations/026_certificados_digitales.sql` — extensión `ades_certificados` + tabla `ades_llaves_firma` + vista `ades_v_certificados_verificacion` + función `revocar_certificado()`
- [x] `backend/app/services/firma_digital.py` — Ed25519 sign/verify, SHA-256 hash, QR PNG base64
- [x] `backend/app/api/v1/certificados.py` — 7 endpoints: listar, emitir (PDF+firma automática), firmar, verificar (público), generar par, registrar llave, llave activa
- [x] `backend/requirements.txt` — `qrcode[pil]==8.1` añadido
- [x] Template `certificado.html` — QR embebido + badge de firma Ed25519
- [x] `frontend/.../certificados/certificados.component.ts` — KPI strip, tabla, dialogs emitir/firmar/llave
- [x] `frontend/.../verificar/verificar.component.ts` — página pública /verificar/:folio sin auth
- [x] `frontend/app.routes.ts` — rutas `/certificados` (auth) + `/verificar/:folio` (público)
- [x] `core/services/api.service.ts` — método `postBlob()` añadido
- [x] Shell menu — "Certificados Digitales" en sección Reportes
- [x] `DECISIONS/0004-firma-digital-ed25519.md` — ADR documentado
- [x] Migración 026 aplicada a BD
- [x] Backend + Frontend reconstruidos (sin cache) y desplegados

### 🚨 Lecciones Aprendidas (2026-06-10):
- **`ADD CONSTRAINT IF NOT EXISTS` no existe en PostgreSQL** — usar `DO $$ BEGIN ... EXCEPTION WHEN duplicate_object THEN NULL; END $$` para idempotencia.
- **`ades_personas` columnas:** `nombre`, `apellido_paterno`, `apellido_materno`, `curp` (NO `nombres`/`primer_apellido`/`segundo_apellido`)
- **`ades_grupos` no tiene `plantel_id`** — la ruta es `grupos → grados → plantel_id`, o directamente `ades_estudiantes.plantel_id`
- **Docker image base pinning:** `python:3.12-slim` ahora apunta a Debian trixie (13), donde `libpangocairo-1.0-0`, `libgdk-pixbuf2.0-0`, `libglib2.0-0` etc. no existen. Siempre usar `python:3.12-slim-bookworm` para estabilidad.
- **redbeat no disponible en ARM64/Py3.12:** Solo existe `0.0.1` en este entorno. Eliminado de requirements.txt; Celery beat usa file-based schedule por defecto. Los `redbeat_*` config keys se ignoran silenciosamente.
- **anthropic==0.49.0 incompatible con langchain-anthropic==0.3.15:** langchain-anthropic 0.3.15 requiere `anthropic>=0.52.0`. Actualizar anthropic a 0.52.0+.
- **`FIRMA_CLAVE_PRIVADA_HEX` en `.env`:** La llave privada Ed25519 NUNCA va a BD. Generar con `firma_digital.generar_nuevo_par_de_llaves()` y guardar en `.env`.

### 🔧 Fix aplicado post-FASE 27 (2026-06-10 — sesión continuación):
- [x] **`promedio_final` normalización Ed25519:** PostgreSQL devuelve `Decimal('9.50')` desde columna NUMERIC; `str()` produce `'9.50'` ≠ `'9.5'` usado al firmar. Fix en `certificados.py` líneas 260, 332, 395: usar `str(float(v))` en lugar de `str(v)` para normalizar consistentemente.
- [x] **Test integración E2E completo:** emitir → firmar → verificar desde BD → detectar alteración → generar PDF 26KB — todos ✓
- [x] **Endpoint público verificado vía HTTPS:** `GET /api/v1/certificados/verificar/{folio}` → `{"autenticidad":"VERIFICADO","firma_valida":true}` ✓
- [x] Backend reconstruido y desplegado con normalization fix.

### 🛠️ FASE 4B — Learning Paths IA completada (2026-06-10):
- [x] **Celery worker + beat levantados** — `psycopg2-binary` añadido a requirements.txt, `SECRET_KEY`/`VALKEY_URL` añadidos al docker-compose.
- [x] **`scan_alertas_todos_grupos` corregido** — `a.estatus` → `a.estatus_asistencia`, `a.fecha` → join con `ades_clases.fecha_clase`. Genera 1297 alertas (1080 reprobación ALTO, 216 MEDIO, 1 ausentismo).
- [x] **Migración 028** — columnas `ia_recomendacion JSONB` en `ades_lp_asignaciones`, `ia_analisis JSONB` en `ades_alertas_academicas`, columnas de auditoría en `ades_lp_recursos`/`ades_lp_asignaciones`, 23 recursos en 4 learning paths.
- [x] **Endpoint `POST /learning-paths/asignaciones/{id}/recomendar-ia`** — llama Claude Haiku con historial académico del alumno, guarda JSON en `ia_recomendacion`.
- [x] **Endpoint `GET /ai/alertas/resumen`** — conteo de alertas agrupado por tipo/nivel.
- [x] **LearningPathsComponent** — KPI strip (1297 alertas), botón ✨ en tabla, dialog IA con análisis (resumen, fortalezas, áreas, estrategias, recursos priorizados, frase motivacional).
- [x] **Fix severity** — `severity="warning"` → `severity="warn"` en certificados.component.ts.
- [PENDIENTE] `ANTHROPIC_API_KEY` en `.env` (actualmente vacío) — necesario para que el endpoint IA funcione.

### 🚨 Lecciones Aprendidas (FASE 4B):
- **`ades_asistencias` no tiene columna `fecha`** — la fecha de la asistencia está en `ades_clases.fecha_clase` via `clase_id`.
- **`ades_asistencias.estatus` → `estatus_asistencia`** — nombre real de la columna.
- **Celery tasks con psycopg2** — el worker usa SQLAlchemy síncrono que requiere `psycopg2-binary`; no se incluía en requirements.txt.
- **Celery beat necesita todas las vars de entorno** del Settings Pydantic (VALKEY_URL, SECRET_KEY), no solo las de broker.
- **Logging estándar**: `log.info(msg, key=val)` no es válido en stdlib logging. Usar `log.info("msg key=%s", val)`.

### 🔧 Bugs funcionales corregidos (2026-06-11):

**Backend:**
- [x] **profesores.py** — `le=200` → `le=1000` para aceptar `por_pagina=500` del frontend
- [x] **admin.py `UsuarioAdminOut`** — cambiado de `AdesResponse` → `AdesSchema` + `id: uuid.UUID` explícito. `AdesResponse` requiere campos de auditoría que no se pasan en construcciones manuales → 500.
- [x] **models/materias.py `Tema`** — reescrito para reflejar la BD real: `materia_id + grado_id + ciclo_escolar_id + orden + periodo_sugerido` (no `materia_plan_id + numero_tema + horas_estimadas`).
- [x] **schemas/materias.py `TemaOut`** — campos actualizados para coincidir con modelo y BD.
- [x] **api/v1/materias.py temas handlers** — 4 handlers (GET/POST/PUT/DELETE de temas) actualizados: lookup `MateriaPlan` → usar `materia_id`/`grado_id` para filtrar; `TemaCreate`/`TemaUpdate` usan `orden`/`periodo_sugerido`.
- [x] **api/v1/materias.py `estadisticas_materia`** — join roto con `CalificacionPeriodo.materia_plan_id` (columna inexistente) → filtrado directo por `CalificacionPeriodo.materia_id`.
- [x] **schemas/academica.py `CicloOut`** — añadido `nombre_nivel: str | None = None`
- [x] **api/v1/catalogs.py `/catalogs/ciclos`** — eager load `nivel` relationship, poblar `nombre_nivel` en response.

**Frontend:**
- [x] **admin.component.ts** — endpoint `/ciclos-escolares` → `/admin/ciclos` (404 → 200)
- [x] **calificaciones.component.ts** — añadido `ciclo_id` al fetch de `/planes-estudio` (materias vacías en calificaciones)
- [x] **planes-estudio.component.ts** — reescritura completa:
  - `Tema` interface: campos actualizados (`materia_id`, `grado_id`, `orden`, `periodo_sugerido`)
  - `nivelActivo = signal('')` (era `= ''`) — computed ahora reacciona
  - Temario cascade: **Nivel → Grado → Materia** (era Nivel → Materia → Grado); backing signals + getter/setter
  - Grados ordenados Primaria→Secundaria→Preparatoria en `ngOnInit`
  - `nivelActivoNombre` computed + label visual en mapa curricular
  - `gradosParaTemario` / `materiasParaTemario` computeds reactivos
- [x] **shell.component.ts** — ciclos postprocesados con `_label` = `nombre_ciclo — NIVEL` cuando se muestran todos los niveles
- [x] **core/models/index.ts** — `CicloEscolar` interface: añadido `nombre_nivel?`, `_label?`
- [x] **profesores.component.ts** — importado `ImportButtonComponent` + `recargar` method + botón en template

### 🛠️ Tareas Completadas (2026-06-11) — SB-012/013/014 Sanciones y Planes de Mejora:

**Migración:**
- [x] `db/migrations/034_sanciones_planes_mejora.sql` — 3 tablas nuevas + trigger:
  - `ades_sanciones_disciplinarias` (SB-012): tipos CHECK, estado, notificación padres, autorizado_por
  - `ades_planes_mejora` (SB-013): compromisos JSONB (alumno/padre/escuela), firmas, estado máquina de estados
  - `ades_seguimiento_plan` (SB-014): avance CHECK, trigger `trg_actualizar_estado_plan` actualiza estado del plan
  - Triggers de auditoría en las 3 tablas; migración aplicada a BD

**Backend `backend/app/api/v1/conducta.py` — 9 endpoints nuevos:**
- [x] `GET /conducta/{id}/detalle-completo` — reporte + sanción + plan + seguimientos en una sola query
- [x] `GET /conducta/alumno/{est_id}/historial` — historial disciplinario completo por alumno
- [x] `POST /conducta/{id}/sancion` — aplicar sanción formal (nivel_acceso ≤ 2, Director)
- [x] `PATCH /conducta/{id}/sancion/{sid}` — actualizar estado/notificación
- [x] `POST /conducta/{id}/plan-mejora` — crear plan (nivel_acceso ≤ 3, Coordinador)
- [x] `PATCH /conducta/{id}/plan-mejora/{pid}` — actualizar firmas/estado
- [x] `POST /conducta/{id}/plan-mejora/{pid}/seguimiento` — agregar seguimiento (trigger actualiza plan)

**Frontend `conducta.component.ts` — reescritura completa:**
- [x] Dialog "Detalle completo" con 4 tabs: Reporte / Sanción / Plan de Mejora / Seguimientos
- [x] Tab Sanción: form crear (solo Director) + actualizar estado/notificación por padres
- [x] Tab Plan: editor compromisos JSONB (agregar/eliminar por tipo: alumno, padre, escuela)
- [x] Tab Seguimientos: historial con avance codificado por color + form nuevo seguimiento
- [x] RBAC en template: `puedeAplicarSancion` (nivel≤2) / `puedeGestionarPlan` (nivel≤3)
- [x] TypeScript limpio: 0 errores de compilación

### 🛠️ Tareas Completadas (2026-06-11) — FASE 31: Operatividad Avanzada + Fix CRUDs Admin:

**Migración:**
- [x] `db/migrations/042_operatividad_avanzada.sql` — `ades_condiciones_cronicas`, `ades_justificaciones_falta`, ALTER asistencias+horarios, view `v_conflictos_horario`

**Backend — 2 routers nuevos + 5 endpoints extendidos:**
- [x] `condiciones_cronicas.py` — GET/POST/PATCH/DELETE + alerta emergencia SB-006/007 (SQLAlchemy AsyncSession + text())
- [x] `justificaciones.py` — GET/POST/resolver OA-003
- [x] comunicados reporte-lectura (CO-005), reinscripcion no-adeudo (PE-016), horarios cambio+conflictos (AC-018/019), profesores reasignar (DP-010)

**Frontend — 2 componentes nuevos:**
- [x] `CondicionesCronicasComponent` — tabla + dialog crear/editar + alerta emergencia
- [x] `JustificacionesComponent` — tabla + stats + aprobar/rechazar

**Fix CRUDs Admin (bug principal reportado):**
- [x] `admin.component.ts` — 6 stubs reemplazados: ciclos (POST/PATCH `/admin/ciclos`), planteles (PATCH `/admin/planteles/{id}`), grupos (POST/PATCH `/admin/grupos`)
- [x] Signals añadidos: `dlgCicloVisible`, `cicloEdit`, `guardandoCiclo`, `dlgPlantelVisible`, `plantelEdit`, `guardandoPlantel`, `dlgGrupoAdminVisible`, `grupoAdminEdit`, `guardandoGrupo`, `grados`
- [x] `cargarGrados()` → `/catalogs/grados`

**Fix TypeScript (6 componentes FASE 29-31):**
- [x] `primeng/calendar` → `primeng/datepicker`, apex-notification import, `notify.warn()` → `notify.warning()`, mesOpts syntax
- [x] Backend: `get_db` import y SQLAlchemy pattern corregidos

**Cobertura CUs:** 165 → 173/230 (71.7% → 75.2%)
**Deployments:** ades-api + ades-frontend rebuilded + running

### 🚀 Próximos Pasos:
- [ ] Configurar `ANTHROPIC_API_KEY` en `.env` para activar recomendaciones IA.
- [ ] FASE 5B — Anclaje Polygon PoS blockchain.
- [ ] FASE 24P — Paperless-ngx OCR expedientes.
- [ ] Setup Authentik: cambiar contraseña akadmin, crear app OIDC ades-frontend.
- [ ] Google Workspace SSO en Authentik para personal @institutonevadi.edu.mx.
- [ ] Construir imagen ades-api (FastAPI backend — FASE 1).
- [ ] Construir imagen ades-frontend (Angular — FASE 1).
- [ ] Script `003_uuid_migration.sql`: migración real de BIGINT → UUID en BD existente (requiere aprobación DBA y ventana de mantenimiento).
- [ ] Crear aplicación OIDC `superset` en Authentik.
- [x] Schema migrado a UUID v7 (`uuidv7()` nativo PG18) — todos los PKs y FKs.
- [x] Estructura académica completa: Ixtapan sec 3°, Metepec prep sem 1-6, Tenancingo prep sem 1-6.
- [x] 39 grados, 78 grupos (66 activos), 168 profesores, 1980 alumnos, 2054 usuarios.
- [x] Seed 002 v4 + 003 v4 con is_active en grupos futuros y auth local para docentes.
- [x] FASE 1 backend: 30 operaciones REST activas (planteles, grupos, materias, alumnos, profesores, usuarios).
- [x] FASE 2 operación: 24 operaciones adicionales (clases, asistencias, calificaciones, tareas).
  - Calificaciones: libreta interactiva + boleta por alumno
  - Asistencias: registro por clase + reportes grupo/alumno
  - Tareas: CRUD + entregas con MinIO + calificación
- [x] Roles ampliados a 14 (SUBDIRECTOR, COORD_ADMIN, COORD_RH, ORIENTADOR, SECRETARIA_ACADEMICA, PREFECTO).
- [x] Frontend Angular 22 scaffold: ContextService, AuthService, ApiService.
  - ShellComponent (topbar + sidebar APEX-style)
  - CalificacionesComponent (Editable Interactive Report con p-cellEditor)
  - Stubs: dashboard, alumnos, profesores, grupos, asistencias, tareas
  - Dockerfile + nginx para producción
  - Autenticación OIDC con Authentik
- [x] Documentación: CONTEXT.md con 14 roles, patrones APEX, UX rules
- [x] Total: 54 operaciones REST + 9 componentes Angular
- [x] Completar features frontend (AlumnosComponent, AsistenciasComponent, etc.)
- [x] DashboardComponent con datos reales vía GET /stats/resumen
- [x] CalificacionesComponent: guardarCambios() real con periodo_evaluacion_id correcto
- [x] Paleta institucional Instituto Nevadi (#D02030) — NevadiPreset en Aura
- [x] styles.scss global: variables CSS, sidebar/topbar rojo institucional
- [x] Migración PrimeNG: p-dropdown → p-select (DropdownModule → SelectModule)
- [x] Build producción exitoso: 0 errores, 517 kB (warning budget leve)
- [x] Backend: GET /stats/resumen (alumnos, profesores, grupos, clases hoy)
- [x] Backend: LibretaGrupo incluye periodos_detalle (id + nombre) para guardar calificaciones reales
- [x] FASE 3 backend: modelos (Aula, Horario, DisponibilidadDocente, PersonalSalud, ExpedienteMedico, IncidenteMedico, ReporteConducta, ReporteAcademico)
- [x] FASE 3 backend: schemas fase3.py + endpoints horarios.py, medico.py, conducta.py
- [x] FASE 3 backend: exportar XML para aSc TimeTables (GET /horarios/exportar-asc/{ciclo_id})
- [x] FASE 3 frontend: HorariosComponent (grid semanal 5×N, vista grupo/docente)
- [x] FASE 3 frontend: ConductaComponent (lista + filtros + dialog nuevo reporte)
- [x] FASE 3 frontend: MedicoComponent (buscar alumno → expediente + incidentes)
- [x] Tipografía: Jost (headings/KPIs) + Inter (tablas/body) — Google Fonts en index.html
- [x] Sidebar con grupos de navegación (Principal / Académico / Operaciones)
- [x] Total API: ~70 operaciones REST (FASE 1 + 2 + 3)
- [x] Total frontend: 12 componentes Angular
- [x] FASE 3 completa: Evaluación Docente 360° (criterios ponderados, tipos evaluador, promedio global)
- [x] FASE 3 boletas PDF: WeasyPrint + Jinja2, template HTML institucional (rojo Nevadi, logo, firmas)
  - GET /boletas/{estudiante_id} → StreamingResponse PDF
  - Template: header, datos alumno, tabla de calificaciones por materia/periodo, firmas
- [x] FASE 4 backend: Asistente pedagógico IA (Claude Sonnet 4.6 vía Anthropic SDK)
  - POST /ai/chat — historial de conversación, contexto de plantel/ciclo
  - GET  /ai/alertas — alertas activas del grupo
  - POST /ai/alertas/scan/{grupo_id} — detección automática de riesgo (reprobación < 6.0, ausentismo < 80%)
- [x] FASE 4 frontend: IaComponent — chat conversacional + panel de alertas académicas
  - Chips de sugerencias rápidas
  - Renderizado markdown básico (negritas, listas, párrafos)
  - Indicador de "escribiendo..." (3 puntos animados)
- [x] Migración 002: tablas ades_criterios_eval_docente, ades_evaluacion_docente, ades_eval_docente_criterios, ades_ai_conversaciones, ades_alertas_academicas
- [x] ExportService Angular: CSV, XLSX (SheetJS), URL-download — patrón Oracle APEX
  - AlumnosComponent: botones CSV + Excel en página header
- [x] SheetJS (xlsx@0.18.5) instalado
- [x] requirements.txt: weasyprint==63.1, jinja2==3.1.5, anthropic==0.49.0, langchain==0.3.25, langchain-anthropic==0.3.15
- [x] Total API: ~85 operaciones REST (FASE 1+2+3+4)
- [x] Total frontend: 15 componentes Angular (+ EvalDocente)
- [x] Exportación CSV/XLSX aplicada a todas las tablas: profesores, grupos, conducta (+ alumnos de sesión anterior)
- [x] EvalDocenteComponent creado: resumen KPI por tipo evaluador, form criterios ponderados 1-5, exportación CSV/Excel
- [x] Ruta /eval-docente + sidebar link "Eval. Docente 360°" en grupo Inteligencia
- [x] Backend Dockerfile: dependencias WeasyPrint (libpango, libcairo, libgdk-pixbuf, libffi)
- [x] Migración 002 ejecutada: ades_criterios_eval_docente (7 seeds), ades_evaluacion_docente, ades_eval_docente_criterios, ades_ai_conversaciones, ades_alertas_academicas
- [x] Build Angular: 0 errores, budget ajustado a 600kB/1.5MB (15 componentes)
- [x] Celery workers: celery_app.py + task boletas batch (grupo→ZIP→MinIO) + task notificaciones internas + beat schedule (scan alertas nocturno + refresh vistas BI/hora)
- [x] Superset BI: superset_config.py (Redis caché, idioma español, feature flags) + 5 vistas materializadas en esquema ades_bi (asistencia_diaria, calificaciones_grupo, riesgo_academico, resumen_plantel, cobertura_curricular) + rol superset_ro
- [x] Migración 003 ejecutada: índice notificaciones, columna notificada en alertas, schema ades_bi, 5 MVs, 4 tablas LP, 4 seeds LP
- [x] Learning Paths: 4 tablas (ades_learning_paths, ades_lp_recursos, ades_lp_asignaciones, ades_lp_progreso) + 8 endpoints REST + LearningPathsComponent (grid de rutas, tabla asignaciones, dialogs nueva ruta / asignar, exportación CSV+Excel, barra de progreso)
- [x] Ruta /learning-paths + sidebar link "Learning Paths" en grupo Inteligencia
- [x] Build Angular 0 errores: 16 componentes, 537 kB inicial, chunk learning-paths 28 kB
- [x] FASE A nginx: proxies activos — ades.setag.mx → ades-frontend:4200, bi.ades.setag.mx → ades-superset:8088
- [x] FASE A redbeat: celery-beat migrado de django_celery_beat a redbeat (Redis-backed, sin Django) — requirements.txt + celery_app.py + docker-compose.yml
- [x] FASE A Authentik: blueprint_oidc.yaml con providers OIDC para ades-frontend y superset; montado en /blueprints/custom del worker
- [x] FASE B backend: comunicados.py (GET/POST/acusar/DELETE, tabla ades_comunicados + ades_acuses_comunicado) + notificaciones.py (no-leidas-count, mis-notificaciones, marcar leída/todas)
- [x] FASE B frontend: ComunicadosComponent (tabla expandible, filtro por tipo, dialog nuevo, acuse de recibo, exportación CSV+Excel)
- [x] FASE B frontend: campanita en ShellComponent topbar — badge con conteo, p-popover con últimas 10 notificaciones, marcar leída al click, "leer todas"
- [x] FASE C backend: grade_analytics.py — tendencias/{grupo_id}, distribucion/{grupo_id}, riesgo, resumen-plantel, cobertura, alertas-umbral (consume vistas materializadas ades_bi)
- [x] FASE C frontend: GradeAnalyticsComponent — 4 tabs (riesgo, tendencias, distribución CSS bar, resumen ejecutivo), KPI cards computados, filtros, exportación
- [x] Sidebar: grupo "Comunicación" (Comunicados), grupo "Inteligencia" ahora incluye Grade Analytics
- [x] Build Angular 0 errores: 18 componentes, 537 kB inicial, grade-analytics 18 kB, comunicados lazy
- [x] FASE 6 backend: evaluaciones.py (programar exámenes ORDINARIO/FINAL/EXTRAORDINARIO, libreta bulk save, estadísticas por evaluación)
- [x] FASE 6 backend: planeacion.py (temas con estado IMPARTIDO/PLANEADO/PENDIENTE, cobertura por materia, crear planeación, marcar impartido)
- [x] FASE 6 backend: rubricas.py (CRUD rúbricas + criterios con niveles_logro JSONB)
- [x] FASE 6 backend: certificados.py (emitir PDF con folio único verificable, GET verificar/{folio} público)
- [x] FASE 6 migración 004: ades_certificados (folio UNIQUE, vigente, tipos), índice rubricas, columna niveles_logro en criterios
- [x] FASE 6 frontend: EvaluacionesComponent — agenda de exámenes, libreta editable bulk save, exportación CSV+Excel
- [x] FASE 6 frontend: PlaneacionComponent — grid kanban de temas por materia con estados, KPIs cobertura, dialog planear, marcar impartido
- [x] FASE 6 frontend: RubricasComponent — panel split lista/builder, criterios con 4 niveles de logro, ponderación
- [x] Sidebar: Académico ampliado (Evaluaciones + Planeación), nuevo grupo Recursos (Rúbricas)
- [x] Build Angular 0 errores: 21 componentes, 537 kB inicial
- [x] FASE 7 migración 005: ades_encuestas + ades_encuesta_preguntas + ades_encuesta_respuestas (seed: encuesta clima escolar con 4 preguntas)
- [x] FASE 7 backend: encuestas.py — CRUD encuestas, preguntas, bulk responder (idempotente por sesion_id), resultados estadísticos por tipo (ESCALA_5/OPCION_MULTIPLE/BOOLEANO/TEXTO_LIBRE), toggle activa
- [x] FASE 7 frontend: EncuestasComponent — dos paneles (lista + detalle), tab Preguntas (diseñador), tab Resultados (estrellas ESCALA_5, barras OPCION_MULTIPLE, SÍ/NO BOOLEANO, citas TEXTO_LIBRE), tab Responder (formulario interactivo)
- [x] Build Angular 0 errores: 22 componentes, 537 kB inicial, encuestas-component 35 kB
- [x] Sidebar: Comunicación → Encuestas (pi-chart-pie)
- [x] FASE 8 migración 006: ades_badges + ades_badge_otorgados (8 seeds: Asistencia Perfecta, Excelencia Académica, etc.)
- [x] FASE 8 backend: badges.py — CRUD catálogo, GET alumno/{id} (earned/unearned), POST otorgar manual, DELETE revocar, POST auto-evaluar/{ciclo_id} (pct_asistencia/promedio_general/sin_reportes_conducta)
- [x] FASE 8 frontend: BadgesComponent — catálogo grid (icon+color+tipo), tab Alumnos (autoComplete→galería earned/unearned), tab Auto-Evaluación (selector ciclo + ejecutar)
- [x] FASE 9 backend: portal.py — GET /buscar, GET /{id}/resumen (360°: KPIs+alertas+badges+LP), GET /{id}/calificaciones (agrupado por materia+periodos), GET /{id}/asistencias, GET /{id}/tareas
- [x] FASE 9 frontend: PortalComponent — buscador autoComplete, tarjeta alumno (avatar+KPI strip), alertas banner, 4 tabs (calificaciones tabla pivot, asistencias resumen+detalle, tareas+pendientes toggle, perfil con badges+LP+datos)
- [x] Build Angular 0 errores: 24 componentes, 535 kB inicial, portal-component 23.8 kB, badges lazy
- [x] FASE 10 migración 007: ades_esquemas_ponderacion + ades_items_ponderacion (3 esquemas base: Primaria SEP, Secundaria SEP, UAEMEX Prep.)
- [x] FASE 10: ALTER TABLE ades_niveles_educativos (escala_maxima, minimo_aprobatorio)
- [x] FASE 10: ALTER TABLE ades_tareas (tipo_item, plan_trabajo_id, rubrica_id, fecha_examen, instrucciones_url)
- [x] FASE 10: ALTER TABLE ades_tareas_entregas (archivo_url, calificacion_obtenida, comentario_profesor, calificado_por)
- [x] FASE 10: ALTER TABLE ades_calificaciones_periodo (score_por_item JSONB, calificacion_calculada, ajuste_manual, justificacion_ajuste, fecha_calculo, fecha_cierre, cerrada)
- [x] FASE 10: Función calcular_calificacion_periodo() — idempotente, PL/pgSQL, soporta examen/tarea/proyecto/asistencia/comportamiento
- [x] FASE 10: 3 triggers automáticos (tareas_entregas, calificaciones_evaluaciones, asistencias)
- [x] FASE 10 backend: esquemas_ponderacion.py (CRUD + efectivo por materia)
- [x] FASE 10 backend: actividades.py (CRUD + calificar masivo + generar slots por alumno)
- [x] FASE 10 backend: entregas.py (subir archivo MinIO + calificar + excusa + pendientes grupo)
- [x] FASE 10 backend: gradebook.py (tabla grupo/período, boleta alumno, ajuste manual, recalcular todo, concentrado, cobertura curricular)
- [x] FASE 10 frontend: GradebookComponent — spreadsheet actividades, concentrado, cobertura curricular, drawer calificar, ajuste manual
- [x] FASE 10 frontend: MiProgresoComponent — cards materias con % progreso, pendientes countdown, historial, subir archivo
- [x] FASE 10 frontend: PonderacionConfigComponent — CRUD esquemas con validación suma=100%, expansion de ítems
- [x] Sidebar: nuevo grupo "Gradebook" (Gradebook, Mi Progreso, Ponderaciones)
- [x] Build Angular: 0 errores, 27 componentes, 540 kB inicial
- [x] Migración 008: 4 roles nuevos (TUTOR, APOYO_ACADEMICO, APOYO_ADMINISTRATIVO, COORDINADOR_AREA), tabla ades_areas_academicas (8 áreas), tabla ades_coordinaciones_area
- [x] DIRECTOR actualizado: puede ser por nivel educativo dentro del plantel — hasta 3 por plantel
- [x] Restricción "1 docente de inglés por plantel" eliminada — sin límite por materia
- [x] Frontend container (ades-frontend) iniciado — ng serve en puerto 4200
- [x] nginx.conf actualizado: resolver 127.0.0.11 + upstreams por variable (DNS diferido, resiliente a restart order)
- [x] ades.setag.mx sirve Angular SPA correctamente (HTTP 200)
- [x] ades-superset iniciado
- [ ] Google Workspace SSO: pendiente credenciales Google Cloud Console de Nevadi
- [ ] Superset: primer arranque manual (superset db upgrade + init + crear datasource ADES apuntando a ades_bi)

---

### 🛠️ Sesión 2026-06-09 — Auditoría APEX / UI-UX Empresarial

**Objetivo:** 100% funcional + Oracle APEX + UI/UX Empresarial Complementaria. Sin avanzar fases nuevas.

#### Correcciones de Infraestructura
- [x] Stirling-PDF: crash por `OutOfMemoryError: Metaspace` → `MaxMetaspaceSize` 128m → 256m, memoria Docker 1G → 1.5G
- [x] Stirling-PDF: healthcheck URL `/` (401) → `/login` (200); start_period 60s → 90s

#### Frontend — APEX / UI/UX Empresarial
- [x] **Dashboard** — rediseño completo:
  - Welcome bar con plantel, ciclo chip y saludo de usuario
  - KPI cards clickeables con routerLink (Oracle APEX pattern)
  - **Gráfico CSS** distribución por nivel educativo (barras horizontales por nivel — nuevo endpoint `/stats/distribucion`)
  - Quick links (8 accesos rápidos)
  - Reactivo a cambio de plantel via `effect()`
- [x] **Alumnos** — filas de tabla clickeables (master-detail APEX)
- [x] **Profesores** — filas de tabla clickeables (master-detail APEX)
- [x] **Tareas** — eliminado fake data `Math.random()`, conectado a API real
- [x] **Conducta** — inputs UUID reemplazados por `p-autoComplete` (LOV alumnos) + `p-select` (grupos)
- [x] **Learning Paths** — inputs UUID en "Asignar alumno" reemplazados por `p-autoComplete` LOV
- [x] **Padres** — tabs Tareas/Conducta conectados a API real
- [x] **Colores hardcodeados** — eliminados en TODOS los componentes (0 instancias):
  - `#94a3b8` → `var(--text-muted)`, `#64748b` → `var(--text-secondary)`, `#1e293b` → `var(--text-primary)`, `#d97706` → `var(--color-warning)`
- [x] **`*ngIf`/`*ngFor` legacy** — migrados a `@if/@for` en 5 archivos:
  - `padres-admin.component.ts`, `comunicados.component.ts`, `ponderacion-config.component.ts`
  - `mi-progreso.component.ts`, `gradebook.component.ts`

#### Backend
- [x] `stats.py` extendido: nuevo endpoint `GET /stats/distribucion` → `list[DistribucionNivel]`

#### Estado de builds
- Production build Angular: ✅ 0 errores / 0 warnings

### 🚀 Próximos Pasos (post-auditoría):
- [ ] Fases 11-16 según roadmap (RBAC UI, admin, manual usuario, Google SSO, auditoría Superset)
- [ ] Verificar Stirling-PDF llega a `healthy` tras restart con nuevo config
- [ ] Superset: primer arranque manual (datasource → dashboards BI)
- [ ] Google Workspace SSO: pendiente credenciales Google Cloud Console de Nevadi

---

### 🛠️ Tareas Completadas (Consolidación Agente Residente - 2026-06-10):
- [x] Ejecutado TASK_01_RESIDENT_AGENT_CONSOLIDATION.md.
- [x] Creación de script `scripts/postgres_memoria_schema.sql` (tablas: memoria.sesiones, memoria.embeddings, memoria.decisiones, pgvector extension).
- [x] Consolidación `.agent/memory/semantic_cache.py` (SentenceTransformer `all-MiniLM-L6-v2`, Valkey/Redis cache, hashing seguro).
- [x] Consolidación `.agent/memory/long_term_memory.py` (Conexión Postgres, `pgvector` embeddings, persistencia de decisiones arquitectónicas y lecciones).
- [x] Documentación actualizada de `.agent/system_prompt.md` integrando principios ECC, OpenSpec y Superpowers.
- [x] Regenerado `docs/resident_agent_genesis.md` versión 2.0 (Master Edition) incorporando la memoria dual y orquestación.
- [x] Tests unitarios creados en `tests/test_resident_agent.py` para Valkey, Postgres, Semantic Cache y Long Term Memory.
- [x] Router backend `agente.py` implementado con `GET /api/v1/agente/init` manejando degradación agraciada (graceful degradation) si no hay memoria.
- [x] Servicio Angular `resident-agent.service.ts` implementado para comunicación con backend.
- [x] `README.md` actualizado con pasos para instanciar el Agente Residente v2.0 e inicializar la memoria (paso 9 en Instalación).
- [x] Ejecutado FASE 26-A: Variables del Sistema y Catálogos Dinámicos (`021_variables_catalogos.sql`).
- [x] Ejecutado FASE 26-B: Menús Dinámicos Integrados.
- [x] Ejecutado FASE 26-C: Privilegios Granulares y Sincronización JIT (Multi-Rol y Authentik).
- [x] Ejecutado FASE 26-D: Notificaciones In-App (APEX alert).
- [x] Ejecutado FASE 26-E: SEPOMEX Geográfico (API y `<app-selector-geo>`).

---

### 🛠️ Sesión 2026-06-10 — APEX Library Integration + FASE 27 Certificación Digital

**Objetivo:** Integrar biblioteca APEX completa en el sistema y arrancar FASE 27.

#### APEX Component Library Integration (completado)
- [x] `ShellComponent`: eliminado `ToastModule` + `providers:[MessageService]`, reemplazado `<p-toast>` por `<apex-toast-container>`
- [x] Menú de navegación: migrado de API dinámica (`/menus/mi-menu`) a `_allNavGroups` estático con 11 secciones, `computed()` filtrado por `ctx.nivelAcceso()`
- [x] **20 feature components** migrados de `MessageService` local a `ApexNotificationService` global
  - Eliminados todos los `providers: [MessageService]`, `ToastModule`, `<p-toast />`
  - Reemplazados todos los `this.msg.add({...})` y `this.toast.add({...})` por `this.notify.success/error/warning/info()`
  - Manejo de template literals en detail: `alumnos`, `profesores`, `ia`, `tareas`, `calificaciones`, `gradebook`, `padres-admin`, `reportes`
- [x] `MessageService` provisto en root (`app.config.ts`) → un solo token, sin instancias aisladas
- [x] Build Angular: 0 errores TypeScript, 0 warnings
- [x] Frontend reconstruido y desplegado
- [x] ADRs creados: 0001 (génesis), 0002 (UUID PKs), 0003 (APEX library), 0004 (firma digital)
- [x] Directorio `DECISIONS/` recreado

#### FASE 27 — Certificación Digital Ed25519 (en progreso)
- [ ] Migración `026_certificados_digitales.sql`
- [ ] `services/firma_digital.py` — Ed25519, QR code
- [ ] `certificados.py` — endpoints firmar + verificar público
- [ ] `requirements.txt` + qrcode[pil]
- [ ] Frontend: `CertificadosComponent` + `/verificar/:folio`
- [ ] Deploy + validación

### 🚀 Próximos Pasos (post APEX Library + FASE 27):
- [x] FASE 27 — Certificación Digital Ed25519 ✅ completa
- [ ] FASE 28 — HashiCorp Vault (gestión segura de llaves privadas)
- [ ] FASE 5 Etapa B — Anclaje Polygon PoS
- [ ] Superset: primer arranque manual (datasource → dashboards BI)
- [ ] Google Workspace SSO: pendiente credenciales Google Cloud Console de Nevadi

---

## Sesión 2026-06-11 — Auditoría 360° + Sprint 1 Fixes Críticos + Sprint 2 Inicio

### 🔑 Estado del Agente:
- **Última Conexión:** 2026-06-11
- **Estado Cognitivo:** Operacional ✅
- **ADRs Registrados:** 0001–0006
- **Migración activa:** 029 (última aplicada)

### 🛠️ Infraestructura (2026-06-11):

| Servicio        | Estado      | Notas |
|-----------------|-------------|-------|
| PostgreSQL 18   | ✅ healthy   | Migración 029 aplicada |
| Valkey 9.1.0    | ✅ healthy   | |
| MinIO           | ✅ healthy   | |
| Authentik       | ✅ healthy   | |
| nginx           | ✅ running   | |
| ades-api        | ✅ healthy   | Sprint 1+2 desplegados |
| ades-frontend   | ✅ running   | roleGuard en 11 rutas |

### 🔬 Auditoría 360° — Hallazgos

| Capa | CRÍTICO | ALTO | MEDIO | BAJO |
|------|---------|------|-------|------|
| Backend | 3 | 6 | 7 | 4 |
| Frontend | 4 | 8 | 7 | 3 |
| Base de datos | 4 | 8 | 7 | 3 |
| **TOTAL** | **11** | **22** | **21** | **10** |

Reporte completo en plan activo (`linked-forging-sprout.md`).

### ✅ Sprint 1 — Fixes Críticos (7/7 completados)

**A — `gradebook.py`:**
- `est.numero_matricula` → `est.matricula` en SQL raw (×2: líneas tabla grupo + concentrado)
- `ajuste_manual`: corregido para `calificacion_final = calificacion_calculada + delta` (antes guardaba el delta como valor absoluto → 1.0)
- `recalcular_periodo`: loop N+1 Python → bulk SQL con `unnest` (280 queries → 1 query)

**B — `db/migrations/029_fixes_criticos.sql` (migración aplicada):**
- `trg_recalcular_desde_asistencia`: referenciaba `cl.ciclo_escolar_id` inexistente → corregido vía `ades_grupos`
- `calcular_calificacion_periodo`: `'TARDANZA'` → `'TARDE'` (match con `ades_asistencias.estatus_asistencia`)
- CHECK constraints: `calificacion_final BETWEEN 0 AND 100`, `calificacion_calculada BETWEEN 0 AND 100`, `fecha_fin >= fecha_inicio` (ciclos y periodos)
- Audit triggers: `ades_bajas`, `ades_extraordinarias`, `ades_constancias`, `ades_cuotas_concepto`, `ades_cuotas_pagos`, `ades_solicitudes_tramites`
- Índices FK: `ades_asignaciones_docentes.profesor_id`, `ades_clases.profesor_id`, `ades_calificaciones_periodo(grupo_id, periodo_evaluacion_id)`

**C — `audit.py` + `security.py` (ADR 0005):**
- Eliminado JWT HS256 decode en `_extract_user` (Authentik emite RS256 → siempre fallaba)
- `get_ades_user` propaga usuario a `request.state.ades_user_id` / `ades_user_nombre`
- Audit trail ahora tiene `usuario_id` correcto en 100% de endpoints mutantes

**D — `calificaciones.py` + `imports.py` (ADR 0006):**
- `get_current_user` → `get_ades_user` en POST/PUT calificaciones + libreta grupo
- Scope plantel: verifica `grupo.plantel_id == ades_user.plantel_id`
- `imports.py`: RBAC `nivel_acceso ≤ 2` en 4 endpoints (alumnos, profesores, materias, grupos)
- Validación MIME + límite 10MB en todos los endpoints de upload (`_validar_archivo`)

**F — `app.routes.ts`:**
- `roleGuard(4)`: calificaciones, asistencias, tareas, conducta, alumnos, horarios, gradebook
- `roleGuard(3)`: profesores, medico, eval-docente, ia, grade-analytics, reportes, grupos
- 11 rutas sensibles protegidas (antes solo `authGuard`)

**H — `reportes.component.ts`:**
- `localStorage.getItem('ades_access_token')` → `inject(AuthService).accessToken()`
- La clave correcta del token es `ades_token` (no `ades_access_token`) en `sessionStorage`

**I — `admin.component.ts`:**
- `console.log('Edit user:', row)` eliminado (exponía datos de usuario en producción)
- Stub documentado: `abrirEditarUsuario(_row)` con TODO explícito

### ✅ Sprint 2 — Altos (2/8 completados)

**E — `admin.py`:**
- `selectinload(Usuario.plantel)` + `selectinload(Usuario.nivel_educativo)` en `listar_usuarios_admin`
- Añadidas relaciones `plantel` / `nivel_educativo` al modelo `Usuario` (`models/personas.py`) con `TYPE_CHECKING` para evitar circular imports
- Elimina loop N+1 de 200 queries por request (`por_pagina=100`)

**G — `shell.component.ts`:**
- `setInterval` → guardado en `private notifInterval` + `clearInterval` en `ngOnDestroy`
- `selectedPlantel`/`selectedNivel` convertidos a signals privados con getter/setter público
- `plantelLabel`/`nivelLabel` convertidos de arrow functions a `computed()` reactivos
- `ShellComponent` implementa `OnDestroy`

### 🚨 Lecciones Aprendidas (2026-06-11):

- **`pg_get_functiondef` incluye el header completo.** Al intentar hacer `replace(funcdef, 'TARDANZA', 'TARDE')` con concatenación manual falla porque la función ya tiene el header. La forma correcta es `EXECUTE replace(pg_get_functiondef(oid), '''TARDANZA''', '''TARDE''')` usando dollar-quoting para las comillas internas.
- **`ades_clases` NO tiene `ciclo_escolar_id`.** El ciclo escolar está en `ades_grupos.ciclo_escolar_id`. Cualquier función PL/pgSQL que necesite el ciclo de una clase debe hacer JOIN via `ades_grupos`.
- **Relaciones ORM en modelos con FK pero sin `relationship()`:** SQLAlchemy con `lazy="raise"` falla silenciosamente si `selectinload()` se llama sobre una relación no declarada. Siempre declarar la relación en el modelo aunque sea con `lazy="raise"` para obligar eager loading explícito.
- **`DO $$ ... EXCEPTION WHEN OTHERS THEN RAISE NOTICE` en migraciones:** permite que el bloque individual falle sin romper la transacción completa. Patrón útil para operaciones idempotentes (CHECK IF NOT EXISTS, función update).
- **Token key mismatch (`ades_token` vs `ades_access_token`):** `AuthService` guarda el token con clave `ades_token` en `sessionStorage`. Cualquier código que use `localStorage.getItem('ades_access_token')` siempre obtiene `null`. Usar siempre `inject(AuthService).accessToken()`.

---

## Sesión 2026-06-11 (continuación) — Sprint 2 Completado

### 🔑 Estado del Agente:
- **Última Conexión:** 2026-06-11
- **Estado Cognitivo:** Operacional ✅
- **ADRs Registrados:** 0001–0006 (+ ADR 0007 pendiente documentar para JWKS async)
- **Migración activa:** 030 (última aplicada — `030_es_acreditado_dinamico.sql`)

### 🛠️ Infraestructura (2026-06-11 sesión continuación):

| Servicio        | Estado      | Notas |
|-----------------|-------------|-------|
| PostgreSQL 18   | ✅ healthy   | Migraciones 001-030 aplicadas. 99 tablas |
| Valkey 9.1.0    | ✅ healthy   | |
| MinIO           | ✅ healthy   | |
| Authentik       | ✅ healthy   | |
| nginx           | ✅ running   | |
| ades-api        | ✅ healthy   | Sprint 2 completo, async JWKS, validator secrets |
| ades-frontend   | ✅ running   | InteractiveGrid en conducta + admin tabs |

### ✅ Sprint 2 — Altos (8/8 completados)

**J — `backend/app/core/security.py`:**
- `httpx.get()` síncrono (bloqueaba event loop hasta 10s) → `httpx.AsyncClient` + `await`
- `@lru_cache` (sin TTL) → TTL cache manual de 5 minutos (`_JWKS_CACHE` + `asyncio.Lock`)
- `_jwks_uri()`, `_fetch_jwks()`, `verify_token()`, `get_current_user()` ahora todos `async`
- No re-descarga JWKS en cada request; expira automáticamente en 300s (resistente a key rotation)

**K — `backend/app/core/config.py`:**
- Añadido `@model_validator(mode='after')` en clase `Settings`
- En `ENVIRONMENT == "production"`: rechaza arranque si `ADES_INTERNAL_API_KEY`, `OIDC_CLIENT_SECRET`, `MINIO_SECRET_KEY` o `NTFY_ADMIN_TOKEN` están vacíos
- Importado `model_validator` desde `pydantic`

**L — `frontend/.../tareas/tareas.component.ts`:**
- `pendientes = () => ...length` (arrow function) → `readonly pendientes = computed(() => ...length)`
- `puedeCrear(): boolean { ... }` (método) → `readonly puedeCrear = computed(() => ...)`
- `computed` añadido al import de `@angular/core`

**M — `frontend/.../dashboard/dashboard.component.ts`:**
- `maxAlumnos(): number { ... }` (método) → `readonly maxAlumnos = computed(() => ...)`
- `maxGrupos(): number { ... }` (método) → `readonly maxGrupos = computed(() => ...)`
- `barPct(value, max)` queda como método (recibe parámetros, no puede ser computed)
- `computed` añadido al import de `@angular/core`

**N — `frontend/.../interactive-grid/interactive-grid.component.ts`:**
- `buscarSugerencias(field, query)` reconstruía distinct values en cada keyup (O(n×k) por tecla)
- Añadido `_suggestionsIndex: Record<string, string[]>` precalculado en `ngOnChanges` cuando `data` cambia
- `_rebuildSuggestionsIndex()` itera columns y precomputa distinct sorted values por campo
- `buscarSugerencias` ahora filtra desde el índice (O(m) en lugar de O(n))

**ALTA-DB-01 — `db/migrations/030_es_acreditado_dinamico.sql`:**
- `es_acreditado` era `GENERATED ALWAYS AS (calificacion_final >= 6.0)` — hardcoded para SEP
- Alumnos UAEMEX/PREPARATORIA con 55/100 aparecían como acreditados (55 ≥ 6.0 = TRUE)
- Solución: drop GENERATED column → regular BOOLEAN + trigger `trg_calificacion_periodo_acreditado`
- Trigger resuelve umbral dinámicamente: `grupo → grado → nivel_educativo → minimo_aprobatorio`
- Backfill: 76,320 registros recalculados con umbral correcto (SEP=6.0, UAEMEX=60.0)
- Modelo SQLAlchemy (`operacion.py`): `Computed(...)` eliminado, column regular `Boolean`

**O — Migrar features a `InteractiveGridComponent`:**
- `admin.component.ts` — tabs ciclos/planteles/grupos migrados a `<app-interactive-grid>`:
  - Añadidas `columnasCiclos`, `columnasPlanteles`, `columnasGrupos` con `ColumnConfig[]`
  - Loaders `cargarCiclos/Planteles/Grupos` aplanan datos con `fecha_inicio_str`, `vigente_str`, `estado_str`, `nivel_grado`, `ocupacion_str`
  - Eliminados 3 bloques `p-table` con templates complejos (tags, date pipes, chips)
  - Acción de editar vía `(rowSelected)` emit → `abrirEditar*()`
- `conducta.component.ts`:
  - Importado `InteractiveGridComponent`, `ColumnConfig`
  - Añadida `columnasReportes: ColumnConfig[]`
  - `cargar()` aplana datos: `medida_aplicada ?? '—'`, `seguimiento_str` desde `requiere_seguimiento`
  - `p-table` de reportes reemplazado por `<app-interactive-grid>`
  - `abrirDetalle()` stub añadido para `(rowSelected)`

### 🚨 Lecciones Aprendidas (Sprint 2):
- **`asyncio.Lock()` en module-level Python 3.12 es seguro**: no se ata al event loop en creación, solo en primer `async with`. Válido para TTL caches a nivel de módulo.
- **`Computed(persisted=True)` en SQLAlchemy no puede referenciar otras tablas**: PostgreSQL GENERATED columns son solo expresiones sobre columnas de la misma fila. Para lógica que involucre JOINs, usar trigger `BEFORE INSERT OR UPDATE`.
- **InteractiveGrid renderiza con `{{ rowData[col.field] }}`**: datos con tags/badges deben aplanarse a strings antes de pasar al grid. La transformación va en el loader (`.map()`), no en el template.
- **`as any` en loaders TypeScript**: cuando el tipo declarado del signal (`signal<CicloAdmin[]>`) no incluye los campos aplanados (`fecha_inicio_str`), usar `flat as any` es preferible a extender la interface solo para display.

### 🚀 Tareas Completadas hoy (2026-06-11 — sesión continuación):

**FASE 27.1 — Backup Automático y Recuperación ante Desastres (DRP):**
- [x] `scripts/backup_postgres.sh` — Script bash para realizar backups de base de datos ADES, Authentik y globales con compresión gzip y rotación automática de 30 días.
- [x] `scripts/backup_minio.sh` — Script bash para sincronizar (mirror) bidireccionalmente los buckets de archivos de MinIO al almacenamiento persistente.
- [x] `docker-compose.yml` — Añadido bind mount de volumen `./backups:/backups` en el contenedor `ades-minio` para persistir los espejos.
- [x] `.gitignore` — Añadido el directorio `backups/` para evitar subir volcados y copias locales al repositorio de git.
- [x] `docs/disaster_recovery_plan.md` — Documentado el Plan de Recuperación ante Desastres (DRP) detallado, incluyendo RPO (24 horas), RTO (2 horas), comandos de recuperación paso a paso para PostgreSQL/MinIO y configuración de cron jobs.
- [x] **Validación y Pruebas**: Ejecución manual exitosa de ambos scripts. Se realizó una prueba de restauración real (creación de tabla de prueba, eliminación de la misma y recuperación íntegra a partir del dump) con resultado exitoso.

**Fase 27 / 28 — Certificación Digital y Acciones Dinámicas:**
- [x] `projects/apex-component-library/.../dynamic-actions/dynamic-action-target.directive.ts` — Creado el componente receptor `ApexDynamicActionTargetDirective` (`[apexDATarget]`) que reacciona a los eventos del servicio `ApexDynamicActionService` (`show`, `hide`, `enable`, `disable`, `refresh`).
- [x] `public-api.ts` — Exportado el nuevo componente receptor en el API público de la librería.
- [x] `frontend/.../certificados/certificados.component.ts` — Integradas las directivas `[apexDATarget]` en los renglones de *Grado Completado* y *Promedio Final* del formulario de emisión para mostrarlos u ocultarlos reactivamente según el tipo de certificado seleccionado, emulando la UX interactiva de Oracle APEX.
- [x] **Validación de Compilación**: Comprobado que la aplicación de producción del frontend compila limpiamente sin advertencias o errores (`npm run build`).

### 🚨 Lecciones Aprendidas (2026-06-11):
- **Dynamic Actions Target-Trigger Pattern**: En Angular 22, diseñar directivas separadas para triggers (`[apexDA]`) y targets (`[apexDATarget]`) comunicados por un `Subject` de RxJS desacopla la lógica interactiva de la vista y replica fielmente el diseño nativo de Oracle APEX.
- **pg_dump vs pg_dumpall**: En entornos multi-base de datos hospedados en el mismo contenedor (como `ades` y `authentik`), respaldar los globales con `pg_dumpall --globals-only` es crucial para restaurar usuarios, passwords de bases de datos y roles de forma idéntica en servidores limpios.

---

---

## Sesión 2026-06-11 (cont. 3) — FASE 29 Seguridad Avanzada + RRHH

### 🔑 Estado del Agente:
- **Última Conexión:** 2026-06-11
- **Estado Cognitivo:** Operacional ✅
- **ADRs Registrados:** 0001–0007
- **Migración activa:** 040 (última aplicada — `040_licencias_capacitaciones.sql`)

### 🛠️ Infraestructura (2026-06-11 sesión cont. 3):

| Servicio        | Estado      | Notas |
|-----------------|-------------|-------|
| PostgreSQL 18   | ✅ healthy   | Migraciones 001-040 aplicadas |
| Valkey 9.1.0    | ✅ healthy   | |
| MinIO           | ✅ healthy   | |
| Authentik       | ✅ healthy   | Grupo ADES Admins + strict MFA stage |
| nginx           | ✅ running   | |
| ades-api        | ✅ healthy   | FASE 29 completa — licencias + capacitaciones |
| ades-frontend   | ✅ running   | Rutas /licencias + /capacitaciones |

### ✅ FASE 29 completada (2026-06-11):

**MFA Authentik (AD-023):**
- [x] Grupo `ADES Admins` creado en Authentik (pk: dd6bd4de-c580-4b5f-bfdc-76ad2647c20f)
- [x] Stage `ades-mfa-strict-validation` (TOTP+WebAuthn+Static, not_configured_action=configure)
- [x] FlowStageBinding orden 29 en default-authentication-flow
- [x] ExpressionPolicy `ades-mfa-enforce-admins` — solo corre para ADES Admins group
- [x] PolicyBinding vinculado al stage binding

**Licencias y Permisos de Personal (DP-006):**
- [x] Migración 040: `ades_licencias_personal` + `ades_capacitaciones_docente`
- [x] `backend/app/api/v1/licencias.py` — 7 endpoints con workflow PENDIENTE→APROBADA/RECHAZADA
- [x] `backend/app/api/v1/capacitaciones.py` — 7 endpoints con validación RH
- [x] `frontend/.../licencias/licencias.component.ts` — grid + dialogs + aprobar/rechazar
- [x] `frontend/.../capacitaciones/capacitaciones.component.ts` — grid + resumen hrs + validar
- [x] Rutas en app.routes.ts: roleGuard(2) para ambas
- [x] Shell navigation: sección "Recursos Humanos" con ambas rutas
- [x] Backend + Frontend reconstruidos y desplegados

### 🚨 Lecciones Aprendidas (FASE 29):
- **Authentik PolicyBinding.target**: recibe objeto `FlowStageBinding` directamente (no su UUID). `get_or_create(target=binding_pk)` falla con ValueError.
- **Authentik MFA strict stage**: usar `not_configured_action=configure` en el stage nuevo ADES; el stage default (`default-authentication-mfa-validation`) mantiene `skip` para no romper usuarios existentes.
- **FlowStageBinding sin `enabled` field**: `FlowStageBinding` en Authentik 2026.5.2 no tiene atributo `enabled` en el modelo Python.

### 📊 Cobertura CUs actualizada:
- **Total implementados: 158+/230** (68.7%+) — DP-006, DP-007, AD-023, AD-024 completados
- Próximas: DP-003/004/005 (disponibilidad, expediente laboral, asistencia personal)

### ✅ FASE 30 completada (2026-06-11 sesión 3):

**Componentes entregados:**
- **Migración 041:** `ades_expediente_laboral`, `ades_asistencia_personal`; ALTER TABLE `ades_profesores` (+horas_semana_max, +horas_frente_grupo); ALTER TABLE `ades_comunicados` (+es_recurrente, +periodicidad, +proximo_envio)
- **Backend:** `api/v1/expediente_laboral.py`, `api/v1/disponibilidad.py`, `api/v1/asistencia_personal.py` + endpoints de detección (EV-007/018 en gradebook.py, OA-011 en planeacion.py, CO-007 en comunicados.py)
- **Frontend:** `features/expediente-laboral/`, `features/disponibilidad/`, `features/asistencia-personal/` + rutas + shell nav
- **Cobertura CU:** 158 → **165/230** (71.7%)

### 🚀 Próximos Pasos:
- [ ] **Manual:** Asignar usuarios ADMIN/DIRECTOR/COORD_ACADEMICO al grupo `ADES Admins` en Authentik Admin UI (localhost:9010)
- [ ] PE-016 (verificación no-adeudo), PE-005 (carta aceptación PDF), AC-014 (planes NEE)
- [ ] pgcrypto encripción columnas sensibles (CURP, RFC, num_cuenta_bancaria en ades_expediente_laboral)
- [ ] SB-006/007 (alertas condiciones crónicas + contacto emergencia), DP-010 (reasignar docente)
- [ ] FASE 31: Foros, Gamificación, Evaluación Diagnóstica (CUs pendientes más complejos)
- [ ] Tarea Celery para auto-envío de comunicados recurrentes (`proximo_envio <= now()`)
- [ ] Integrar certificados PDF en sistema (Carbone + Stirling-PDF para ades_expediente_laboral)

---

### 🚀 Próximos Pasos (histórico):

- [ ] **Fase 27.2**: Diseñar la migración DDL `031_reinscripcion.sql` para el flujo de reinscripción masiva.
- [ ] **Fase 27.2 (Backend)**: Desarrollar endpoints en FastAPI para validar adeudos académicos y de cobranza en lote.
- [ ] **Fase 27.3**: Desarrollar el asistente de cierre de período académico de 4 pasos (APEX Wizard pattern).
- [ ] **Fase 27.4**: Implementar notificaciones y resúmenes automáticos por email.
- [ ] Swagger/ReDoc deshabilitado en producción (`main.py`)
- [ ] FASE 28 (inicio): Integración de Paperless-ngx e ingesta de documentos OCR.
