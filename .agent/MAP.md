# 🗺️ Mapa Técnico del Proyecto ADES (MAP.md)
# Última actualización: 2026-06-23

---

## 📂 Estructura de Directorios

```
/opt/ades/
├── .agent/                        # Contexto cognitivo del agente residente
│   ├── AGENT.md                   # Leyes y reglas de operación
│   ├── CONTEXT.md                 # Propósito, institución, módulos, stack (actualizado 2026-06-23)
│   ├── MAP.md                     # Este mapa técnico
│   ├── STATE.md                   # Estado actual y bitácora (actualizado 2026-06-23)
│   ├── HEURISTICS.md              # Heurísticas de toma de decisiones
│   └── RULES.md                   # Reglas de formato y convenciones
│
├── DECISIONS/                     # ADRs 0001-0011
│
├── db/
│   ├── migrations/                # DDL versionado: 001 → 094 + date-based scripts
│   │   ├── 001_initial_schema.sql # Esquema completo FASE 1–2 (57 tablas base)
│   │   ├── ...
│   │   ├── 093_classroom_gaps.sql # Plagio+multimedia+NEE en gradebook
│   │   └── 094_dedup_codigos_postales.sql # Dedup SEPOMEX + constraint UNIQUE
│   └── seeds/                     # 001-009 ejecutados (institución, ciclos, alumnos, calificaciones, portal, gradebook, ponderación, biblioteca, eval360)
│
├── backend/                       # FastAPI — IA, embeddings, insights, render docs
│   └── app/
│       ├── api/v1/                # Routers FastAPI
│       │   ├── router.py          # Registro central
│       │   ├── # ── CORE ────────────────────────────────────────
│       │   ├── health.py, catalogs.py, planteles.py, grupos.py
│       │   ├── materias.py, alumnos.py, profesores.py, usuarios.py, stats.py
│       │   ├── # ── ACADÉMICO ───────────────────────────────────
│       │   ├── clases.py, asistencias.py, calificaciones.py, tareas.py
│       │   ├── # ── ESPECIALIZADO ───────────────────────────────
│       │   ├── horarios.py, medico.py, conducta.py, boletas.py, eval_docente.py
│       │   ├── # ── IA + ANALYTICS ─────────────────────────────
│       │   ├── ai_assistant.py, learning_paths.py, grade_analytics.py
│       │   ├── # ── COMUNICACIÓN + GESTIÓN ─────────────────────
│       │   ├── comunicados.py, notificaciones.py
│       │   ├── # ── EVALUACIÓN + CERTIFICACIÓN ──────────────────
│       │   ├── evaluaciones.py, planeacion.py, rubricas.py, certificados.py
│       │   ├── encuestas.py, badges.py, portal.py
│       │   ├── # ── GRADEBOOK ───────────────────────────────────
│       │   ├── esquemas_ponderacion.py, actividades.py, entregas.py, gradebook.py
│       │   ├── # ── RRHH + OPERATIVIDAD ─────────────────────────
│       │   ├── condiciones_cronicas.py, justificaciones.py
│       │   ├── expediente.py, admin.py, geo.py, movilidad.py
│       │   ├── # ── OPENSOURCE STACK ────────────────────────────
│       │   ├── h5p.py, bbb.py, biblioteca.py, sepomex.py
│       │   └── stats.py
│       ├── core/
│       │   ├── database.py        # AsyncSession, engine PostgreSQL via PgBouncer
│       │   ├── security.py        # JWT decode, get_current_user, get_ades_user (USAR SIEMPRE)
│       │   └── optimistic_locking.py  # check_row_version() para PATCH mutantes
│       ├── services/
│       │   ├── firma_digital.py   # Ed25519 sign/verify, SHA-256, QR PNG
│       │   └── llm_service.py     # LLMService singleton (NVIDIA NIM / Anthropic)
│       ├── worker/
│       │   ├── celery_app.py      # Tasks: boletas batch, notificaciones, alertas scan
│       │   ├── notificaciones.py  # Refresh MVs, push ntfy
│       │   └── ocr.py             # Celery task OCR Paperless-ngx
│       └── tests/                 # test_boleta.py (7 tests), test_alumnos.py, etc.
│
├── backend-spring/                # BFF principal — toda lógica de negocio nueva
│   └── src/main/java/mx/ades/
│       ├── modules/               # 62 módulos hexagonales
│       │   ├── admin/             # Gestión usuarios, ciclos, planteles, grupos (AdminController.java)
│       │   ├── alumnos/           # CRUD + hexagonal completo (ApplicationService + PersistenceAdapter)
│       │   ├── asistencias/       # Pase de lista + resúmenes
│       │   ├── aulas/             # CRUD + disponibilidad + conflictos
│       │   ├── bi/                # BI stats, MVs, KPIs Director Dashboard
│       │   ├── biblioteca/        # Libros + préstamos
│       │   ├── boletas/           # Proxy FastAPI boletas NEM/UAEMEX
│       │   ├── calificaciones/    # Libreta, cualitativa NEM
│       │   ├── certificados/      # Proxy FastAPI Ed25519
│       │   ├── conducta/          # Reportes + sanciones + plan mejora
│       │   ├── evaluaciones/      # Ordinario/final/extraordinario
│       │   ├── eval-docente/      # 360° — 4 tipos, 7 criterios
│       │   ├── geo/               # SEPOMEX, colonias, selector-geo
│       │   ├── grupos/            # CRUD grupos + alumnos
│       │   ├── horarios/          # Grid semanal, aSc XML
│       │   ├── imports/           # Import CSV/Excel (6 módulos)
│       │   ├── kardex/            # UAEMEX constancia calificaciones
│       │   ├── learning-paths/    # Rutas adaptativas + IA
│       │   ├── materias/          # CRUD + hexagonal
│       │   ├── medico/            # Expediente médico + incidentes
│       │   ├── movilidad/         # Alta/baja temporal/permanente
│       │   ├── personal-admin/    # No-docente, RRHH
│       │   ├── planes-estudio/    # NEM + CBU + temarios
│       │   ├── planteles/         # CRUD + hexagonal
│       │   ├── portal/            # Portal externo convocatorias
│       │   ├── procesos/          # Cierre ciclo, promociones
│       │   ├── profesores/        # CRUD + hexagonal
│       │   ├── rbac/              # Roles, permisos, nivel_acceso
│       │   ├── reportes/          # Kardex, 911 SEP + Sección IX, acta evaluación
│       │   ├── reinscripcion/     # Proceso reinscripción
│       │   └── stats/             # Telemetría JVM, Celery, DB (Monitor)
│       └── common/
│           ├── ValidationUtils.java  # CURP, RFC, email, teléfono, fechaNacimiento (año 1900-hoy)
│           ├── AdesUser.java         # DTO resolución JWT → usuario ADES
│           └── HexagonalConfig.java  # Beans ApplicationService
│
├── frontend/
│   └── src/app/
│       ├── core/
│       │   ├── services/
│       │   │   ├── api.service.ts         # HTTP client centralizado (usar siempre, NO HttpClient directo)
│       │   │   ├── auth.service.ts        # OIDC/Authentik, token management
│       │   │   ├── context.service.ts     # plantel + ciclo activos (signals)
│       │   │   └── export.service.ts      # CSV + XLSX (SheetJS)
│       │   └── components/
│       │       ├── callback.component.ts  # OIDC callback
│       │       └── login.component.ts
│       ├── layout/
│       │   └── shell.component.ts         # Sidebar + topbar APEX-style; menú filtrado por nivel_acceso
│       └── features/                      # 59 componentes lazy-loaded
│           ├── # ── ADMINISTRACIÓN ──────────────────────────────
│           ├── admin/                     # Gestión usuarios, ciclos, planteles, grupos, roles
│           ├── planteles/                 # Lista planteles
│           ├── grupos/                    # CRUD grupos con KPIs
│           ├── alumnos/                   # CRUD alumnos + perfil completo
│           ├── profesores/                # CRUD profesores + asignaciones
│           ├── planes-estudio/            # Temarios NEM/CBU, materias institucionales
│           ├── # ── ACADÉMICO ──────────────────────────────────
│           ├── calificaciones/            # Libreta + cascada plantel→nivel→grado→grupo
│           ├── asistencias/               # Pase de lista + resúmenes
│           ├── tareas/                    # CRUD tareas + entregas MinIO
│           ├── gradebook/                 # Spreadsheet masivo, calcular_periodo, NEE, plagio
│           ├── evaluaciones/              # Agenda exámenes + libreta bulk
│           ├── planeacion/                # Kanban temas por materia
│           ├── rubricas/                  # Builder criterios niveles_logro
│           ├── horarios/                  # Grid semanal, dialog CRUD
│           ├── aulas/                     # CRUD aulas + disponibilidad
│           ├── acta-evaluacion/           # Reporte oficial grupo
│           ├── kardex/                    # UAEMEX constancia PDF + cascada
│           ├── estadistica-911/           # Reporte SEP + Sección IX discapacidad
│           ├── # ── REPORTES Y CERTIFICADOS ─────────────────────
│           ├── certificados/              # Emitir, firmar, gestión llaves Ed25519
│           ├── verificar/                 # Verificación pública /verificar/:folio (sin auth)
│           ├── reportes/                  # Reportes consolidados
│           ├── # ── IA + ANALYTICS ─────────────────────────────
│           ├── ia/                        # Chat IA + alertas + sesiones guardadas
│           ├── learning-paths/            # Rutas adaptativas + recomendación IA
│           ├── grade-analytics/           # Tendencias, distribución, riesgo plantel
│           ├── bi/                        # Superset embed 4 dashboards (Director)
│           ├── director-dashboard/        # KPIs rápidos generales (roleGuard ≤ 2)
│           ├── monitor/                   # Telemetría JVM + Celery (roleGuard ≤ 2)
│           ├── # ── CONDUCTA + SALUD ───────────────────────────
│           ├── conducta/                  # Reportes + sanciones + plan mejora + seguimiento
│           ├── medico/                    # Expediente médico + incidentes
│           ├── condiciones-cronicas/      # SB-006/007, alerta emergencia
│           ├── # ── COMUNICACIÓN ───────────────────────────────
│           ├── comunicados/               # Envío con acuse digital
│           ├── foros/                     # Foros por grupo
│           ├── encuestas/                 # 4 tipos de pregunta + resultados
│           ├── # ── GAMIFICACIÓN ───────────────────────────────
│           ├── badges/                    # Catálogo, otorgar, auto-evaluar
│           ├── mi-progreso/               # Vista alumno: tareas + calificaciones + badges
│           ├── # ── RRHH + PERSONAL ────────────────────────────
│           ├── personal-admin/            # No-docente: CRUD personal admin
│           ├── licencias/                 # RRHH licencias con estados
│           ├── capacitaciones/            # RRHH capacitaciones y evaluación
│           ├── disponibilidad/            # Horario docente para aSc
│           ├── expediente-doc/            # Documentos + OCR Paperless buscar
│           ├── expediente-laboral/        # Expediente staff: documentos laborales
│           ├── asistencia-personal/       # Pase de lista staff
│           ├── justificaciones/           # Aprobar/rechazar faltas
│           ├── # ── OPERATIVIDAD ───────────────────────────────
│           ├── admision/                  # Inscripción nueva + PDF admisión
│           ├── reinscripcion/             # Proceso reinscripción ciclo
│           ├── movilidad/                 # Alta/baja temporal/permanente alumno
│           ├── optativas/                 # Inscripción/catálogo optativas
│           ├── cierre-ciclo/              # cerrar_ciclo_y_promover()
│           ├── calendario/                # Vista calendario escolar
│           ├── # ── PONDERACIÓN Y CONFIGURACIÓN ─────────────────
│           ├── ponderacion-config/        # Esquemas ponderación + es_nee
│           ├── # ── PADRES/COMUNIDAD ────────────────────────────
│           ├── padres/                    # Vista padre: hijos, comunicados
│           ├── padres-admin/              # Gestión contactos familiares
│           ├── portal/                    # Vista 360° alumno
│           ├── portal-admin/              # Convocatorias portal externo
│           ├── # ── RECURSOS DIDÁCTICOS ─────────────────────────
│           ├── h5p/                       # Contenido interactivo H5P + iframe player
│           ├── bbb/                       # Videoconferencias BigBlueButton
│           ├── biblioteca/                # Libros + préstamos control ejemplares
│           └── ayuda/                     # Documentación in-app
│
├── infrastructure/
│   ├── nginx/                     # Config proxy reverso + TLS
│   ├── superset/                  # Dockerfile + create_dashboards.py
│   ├── grafana/                   # Dashboards JSON provisioned
│   ├── h5p/                       # Node.js server
│   └── vault/                     # Config + políticas
│
├── integrations/
│   ├── superset/                  # Scripts creación datasets/charts/RLS
│   └── asc_horarios/              # Exportador/importador XML aSc TimeTables
│
├── docs/
│   ├── manual-usuario.md          # Manual completo v2.0 (1526 líneas, 29 módulos)
│   └── plan_pruebas_integral.md   # 341 tests e2e suites 01-17 (74.8% pass rate)
│
├── memory/
│   ├── semantic_cache.py          # SemanticCache Valkey
│   └── long_term_memory.py        # LongTermMemory PostgreSQL + pgvector
│
└── spec/modules/casos-de-uso/
    ├── specification.md           # 12 CUs, 1200+ líneas
    └── matriz-trazabilidad.md     # BD → API → Frontend
```

---

## 🔋 Servicios y Puertos

| Servicio            | Puerto interno | URL pública                           | Estado       |
|---------------------|:--------------:|---------------------------------------|--------------|
| PostgreSQL 18       | 5432           | —                                     | ✅ healthy   |
| PgBouncer           | 6432           | —                                     | ✅ healthy   |
| Valkey 9.1.0        | 6379           | —                                     | ✅ healthy   |
| SeaweedFS (S3+Filer)| 9000 / 8888    | —                                     | ✅ healthy   |
| Authentik server    | 9010           | https://ades.setag.mx/auth/           | ✅ healthy   |
| Spring BFF          | 8080           | https://ades.setag.mx/api/v1/         | ✅ running   |
| FastAPI (IA+docs)   | 8000           | https://ades.setag.mx/api/v1/ (proxy) | ✅ healthy   |
| Angular Frontend    | 4200           | https://ades.setag.mx/                | ✅ healthy   |
| Portal externo      | 4201           | —                                     | ✅ running   |
| Superset            | 8088           | https://bi.ades.setag.mx/             | ✅ healthy   |
| Prometheus          | 9090           | —                                     | ✅ healthy   |
| Grafana             | 3003           | —                                     | ✅ healthy   |
| H5P Node.js         | 8091           | —                                     | ✅ healthy   |
| n8n                 | 5678           | —                                     | ⚠️ starting  |
| ntfy                | 2586           | —                                     | ✅ healthy   |
| Stirling-PDF        | 8081           | —                                     | ✅ healthy   |
| Carbone             | 3001           | —                                     | ✅ healthy   |
| Flowise             | 3002           | —                                     | ✅ healthy   |
| Vault               | 8200           | —                                     | ✅ running   |
| Celery worker/beat  | —              | —                                     | ✅ running   |
| Flower (monitor)    | 5555           | —                                     | ✅ running   |
| nginx               | 80/443         | ades.setag.mx                         | ✅ running   |

---

## 📊 Base de Datos — Tablas Clave por Módulo

| Módulo              | Tablas principales                                                              |
|---------------------|---------------------------------------------------------------------------------|
| Core                | ades_planteles, ades_grupos, ades_inscripciones, ades_ciclos_escolares          |
| Personas            | ades_personas, ades_usuarios, ades_estudiantes, ades_profesores                 |
| Académico           | ades_tareas, ades_tareas_entregas, ades_asistencias, ades_clases                |
| Evaluaciones        | ades_evaluaciones, ades_calificaciones_evaluaciones, ades_evaluacion_docente    |
| **Gradebook**       | ades_esquemas_ponderacion (es_nee), ades_items_ponderacion                      |
|                     | ades_calificaciones_periodo (score_por_item, cerrada, plagio_porcentaje)        |
|                     | calcular_calificacion_periodo() — PL/pgSQL + 3 triggers + NEE cascade          |
| Conducta            | ades_reportes_conducta, ades_sanciones_disciplinarias, ades_planes_mejora       |
| Salud               | ades_expedientes_medicos, ades_incidentes_medicos, ades_condiciones_cronicas    |
| RRHH                | ades_licencias, ades_capacitaciones, ades_asistencia_personal, ades_disponibilidad_docente |
| Certificados        | ades_certificados, ades_llaves_firma, ades_v_certificados_verificacion          |
| Badges              | ades_badges, ades_badge_otorgados                                               |
| Comunicación        | ades_comunicados, ades_notificaciones, ades_encuestas, ades_foros               |
| IA                  | ades_ai_conversaciones, ades_alertas_academicas, ades_learning_paths            |
| BI                  | schema ades_bi — 5 vistas materializadas (ispopulated=true)                    |
| Biblioteca          | ades_biblioteca_libros, ades_biblioteca_prestamos                               |
| H5P                 | ades_h5p_tipos, ades_h5p_contenidos, ades_h5p_asignaciones, ades_h5p_resultados|
| BBB                 | ades_bbb_reuniones, ades_bbb_grabaciones, ades_bbb_asistencia                   |
| Auditoría           | auditoria.log_auditoria (344 triggers audit_biu activos)                        |
| Geo                 | ades_codigos_postales (uq_cp_localidad), ades_colonias, ades_municipios         |
| Menús               | ades_menus (UUID PK), ades_menus_permisos_rol                                   |
| NEM Cualitativa     | ades_config, ades_escalas_evaluacion (A/B/C/D + equiv_num)                      |

---

## 🔑 Patrones de Código Críticos

### JWT → usuario (Spring BFF — OBLIGATORIO)
```java
AdesUser user = userService.resolveUser(jwt);
// Nunca operar sin resolver el usuario primero
// Scoping: user.getPlantelId() para no-admins
```

### JWT → usuario (FastAPI — OBLIGATORIO)
```python
user: AdesUser = Depends(get_ades_user)  # NO get_current_user — ese devuelve dict
# plantel scoping: user.plantel_id para nivelAcceso >= 2
```

### SQL async (FastAPI)
```python
rows = await db.execute(text("SELECT ..."), {"param": value})
return [dict(r._mapping) for r in rows.fetchall()]
```

### Angular signals
```typescript
items = signal<T[]>([]);
computed_val = computed(() => this.items().filter(...));
items.set(newList);
items.update(list => [...list, newItem]);
```

### Cascada plantel → nivel → grado → grupo (OBLIGATORIA en toda UI con selector de grupo)
```typescript
// 4 p-select en cascada; usar computed() signals client-side
plantelSel = signal<string>('');
nivelSel = signal<string>('');
gradoSel = signal<string>('');
grupoSel = signal<string>('');
// Handler: al cambiar plantel → limpiar nivel, grado, grupo
```

### Export
```typescript
this.exporter.toCSV(data, cols, 'filename');          // 3 args
this.exporter.toXLSX(data, cols, 'Sheet', 'filename'); // 4 args
```

### ContextService
```typescript
const plantelId = this.ctx.plantel()?.id;   // ← CORRECTO
// NO usar ctx.plantelId()                  // ← NO EXISTE
```

### LOV (p-select en modals/drawers)
```typescript
// app.config.ts tiene overlayAppendTo: 'body' en providePrimeNG()
// todos los p-select funcionan correctamente dentro de p-dialog/p-drawer
```

### Gradebook — cálculo automático + NEE
La función `calcular_calificacion_periodo(alumno_id, grupo_id, materia_id, periodo_id)`
soporta adecuaciones NEE: si el alumno tiene `ades_nee.activa=TRUE`, usa el esquema
con `es_nee=TRUE` antes del esquema genérico.

---

## 📐 Reglas de Negocio Inmutables

| Regla                        | Detalle                                                       |
|------------------------------|---------------------------------------------------------------|
| Escalas SEP                  | 0–10, mínimo aprobatorio 6.0                                  |
| Escalas UAEMEX               | 0–100, mínimo aprobatorio 60                                  |
| NEM 1°-2° primaria           | Cualitativa A/B/C/D; equiv_num A=10,B=8,C=6,D=4              |
| Suma ponderaciones           | Siempre 100% (validado en POST/PUT esquemas)                  |
| Calificación cerrada         | Solo ADMIN puede modificarla                                  |
| Ajuste manual                | Requiere justificación ≥ 20 caracteres                        |
| Idempotencia triggers        | calcular_calificacion_periodo es idempotente (UPSERT)         |
| Archivos de entrega          | Siempre a SeaweedFS bucket tareas-entregas; nunca filesystem  |
| No eliminar entregas         | Usar estatus SIN_ENTREGA o EXCUSA                             |
| Fecha nacimiento             | Año entre 1900 y año actual (ValidationUtils.validarFechaNacimiento) |
| PKs                          | UUID v7 (uuidv7()), NUNCA SERIAL/BIGINT                       |
| Auditoría                    | audit_biu en TODA tabla ades_*; aiud solo producción          |

---

## 🛡️ Checklist de Seguridad (STRIDE/OWASP)

| Check | Implementación |
|---|---|
| resolveUser(jwt) | OBLIGATORIO en todo endpoint Spring; get_ades_user en FastAPI |
| Scoping plantel | user.getPlantelId() para non-admins (nivel_acceso >= 2) |
| RBAC nivel_acceso | Verificar en cada endpoint; roleGuard(N) en rutas Angular |
| IDOR prevención | Path params usados en lógica; validar ownership con DB |
| Validación input | CURP, RFC, email, teléfono, fechaNacimiento en ValidationUtils.java |
| MIME magic bytes | python-magic en expediente.py; MIME_PERMITIDOS whitelist |
| Optimistic locking | row_version en PATCH/PUT; 409 si versión stale |
| Rate limiting | slowapi en FastAPI endpoints sensibles |
| HTTPS | HTTPSRedirectMiddleware + nginx TLS; HSTS |
| Audit trail | AuditHttpFilter Spring; AuditMiddleware FastAPI; hash MD5 encadenado |
| Secrets | Vault + .env; NUNCA en código fuente |
| SQL injection | JdbcTemplate/SQLAlchemy con params; NUNCA f-string en SQL |

---

## 🔄 Flujo de Autenticación

1. Usuario → `ades.setag.mx` (nginx)
2. nginx → Angular frontend (:4200)
3. Angular → Authentik OIDC (/auth/application/o/ades/)
4. Authentik → código → callback → ID token + access token
5. Token almacenado en sessionStorage (key: `ades_token`)
6. Angular → BFF Spring (:8080) con Bearer token
7. BFF Spring → `JwtDecoder` + `resolveUser(jwt)` → `AdesUser`
8. Para rutas IA/docs: BFF Spring proxea a FastAPI (:8000)
