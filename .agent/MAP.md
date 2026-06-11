# 🗺️ Mapa Técnico del Proyecto ADES (MAP.md)

Este documento describe la estructura de directorios, los archivos clave y las dependencias
del sistema ADES — Instituto Nevadi.

---

## 📂 Estructura de Directorios

```
/opt/ades/
├── .agent/                        # Contexto cognitivo del agente residente
│   ├── AGENT.md                   # Leyes y reglas de operación
│   ├── CONTEXT.md                 # Propósito, institución, módulos, stack
│   ├── MAP.md                     # Este mapa técnico
│   ├── STATE.md                   # Estado actual y bitácora
│   ├── HEURISTICS.md              # Heurísticas de toma de decisiones
│   └── RULES.md                   # Reglas de formato y convenciones
│
├── db/
│   ├── migrations/                # DDL versionado (ejecutar en orden)
│   │   ├── 001_initial_schema.sql # Esquema completo FASE 1–2 (57 tablas base)
│   │   ├── 002_fase3_fase4.sql    # Eval. docente, IA, alertas, conversaciones
│   │   ├── 003_bi_learning_paths.sql # Vistas BI, Learning Paths
│   │   ├── 004_certificados_rubricas.sql # Certificados con folio, rúbricas
│   │   ├── 005_encuestas.sql      # Encuestas con tipos de pregunta
│   │   ├── 006_badges_portal.sql  # Badges/insignias (8 seeds)
│   │   └── 007_gradebook.sql     # Gradebook: ponderaciones, triggers, cálculo automático
│   └── seeds/                     # Datos iniciales (Instituto Nevadi, ciclos, etc.)
│
├── backend/
│   └── app/
│       ├── api/v1/                # Routers FastAPI (175 operaciones REST)
│       │   ├── router.py          # Registro central de todos los routers
│       │   ├── # ── FASE 1 ──────────────────────────────────────
│       │   ├── health.py, catalogs.py, planteles.py, grupos.py
│       │   ├── materias.py, alumnos.py, profesores.py, usuarios.py, stats.py
│       │   ├── # ── FASE 2 ──────────────────────────────────────
│       │   ├── clases.py, asistencias.py, calificaciones.py, tareas.py
│       │   ├── # ── FASE 3 ──────────────────────────────────────
│       │   ├── horarios.py, medico.py, conducta.py, boletas.py, eval_docente.py
│       │   ├── # ── FASE 4 ──────────────────────────────────────
│       │   ├── ai_assistant.py, learning_paths.py
│       │   ├── # ── FASE 5 ──────────────────────────────────────
│       │   ├── comunicados.py, notificaciones.py, grade_analytics.py
│       │   ├── # ── FASE 6 ──────────────────────────────────────
│       │   ├── evaluaciones.py, planeacion.py, rubricas.py, certificados.py
│       │   ├── # ── FASE 7 ──────────────────────────────────────
│       │   ├── encuestas.py
│       │   ├── # ── FASE 8 ──────────────────────────────────────
│       │   ├── badges.py
│       │   ├── # ── FASE 9 ──────────────────────────────────────
│       │   ├── portal.py
│       │   └── # ── FASE 10 (Gradebook) ─────────────────────────
│       │       ├── esquemas_ponderacion.py
│       │       ├── actividades.py
│       │       ├── entregas.py
│       │       └── gradebook.py
│       ├── core/
│       │   ├── database.py        # AsyncSession, engine PostgreSQL
│       │   └── security.py        # JWT decode, get_current_user
│       └── worker/
│           └── celery_app.py      # Tasks: boletas batch, notificaciones, alertas scan
│
├── frontend/
│   └── src/app/
│       ├── core/
│       │   ├── services/
│       │   │   ├── api.service.ts         # HTTP client centralizado
│       │   │   ├── auth.service.ts        # OIDC/Authentik
│       │   │   ├── context.service.ts     # plantel + ciclo activos (signals)
│       │   │   └── export.service.ts      # CSV + XLSX (SheetJS)
│       │   └── components/
│       │       ├── callback.component.ts  # OIDC callback
│       │       └── login.component.ts
│       ├── layout/
│       │   └── shell.component.ts         # Sidebar + topbar + grupos de navegación (estilo Oracle APEX)
│       └── features/                      # 27 componentes lazy-loaded
│           ├── dashboard/                 # KPIs generales
│           ├── planteles, grupos, alumnos, profesores  (FASE 1)
│           ├── calificaciones, asistencias, tareas     (FASE 2)
│           ├── horarios, conducta, medico               (FASE 3)
│           ├── eval-docente, learning-paths, ia         (FASE 4)
│           ├── comunicados, grade-analytics             (FASE 5)
│           ├── evaluaciones, planeacion, rubricas       (FASE 6)
│           ├── encuestas                                (FASE 7)
│           ├── badges                                   (FASE 8)
│           ├── portal                                   (FASE 9)
│           ├── gradebook/            ← FASE 10: panel profesor (spreadsheet)
│           ├── mi-progreso/          ← FASE 10: vista alumno
│           ├── ponderacion-config/   ← FASE 10: admin ponderaciones
│           ├── certificados/         ← FASE 27: emitir, firmar, gestión llaves Ed25519
│           └── verificar/            ← FASE 27: verificación pública /verificar/:folio (sin auth)
│
├── integrations/
│   ├── superset/
│   │   └── Dockerfile             # FROM apache/superset:5.0.0 + psycopg2 + redis
│   └── asc_horarios/              # Exportador XML para aSc TimeTables
│
├── docker-compose.yml             # Servicios: postgres, valkey, minio, authentik,
│                                  # nginx, ades-api, ades-frontend, superset
└── README.md
```

---

## 🔋 Servicios y Puertos

| Servicio            | Puerto interno | URL pública                         | Notas                              |
|---------------------|:--------------:|-------------------------------------|------------------------------------|
| PostgreSQL 18       | 5432           | —                                   | pgvector, uuidv7() nativo          |
| Valkey 9.1.0        | 6379           | —                                   | Caché + Celery broker + redbeat    |
| MinIO               | 9000/9001      | minio.ades.setag.mx                 | Archivos de tareas, entregas       |
| Authentik server    | 9000           | https://auth.ades.setag.mx          | IdP OIDC, SSO Google Workspace     |
| ades-api (FastAPI)  | 8000           | https://ades.setag.mx/api/v1/       | 180+ ops REST (FASES 1–27)         |
| ades-frontend       | 4200           | https://ades.setag.mx/              | Angular 22 + PrimeNG 21 (29 comp.) |
| Superset            | 8088           | https://bi.ades.setag.mx/           | BI sobre schema ades_bi            |
| nginx               | 80/443         | —                                   | TLS Let's Encrypt, reverse proxy   |

---

## 📊 Base de Datos — Tablas Clave por Módulo

| Módulo              | Tablas principales                                                    |
|---------------------|-----------------------------------------------------------------------|
| Core                | ades_planteles, ades_grupos, ades_inscripciones, ades_ciclos_escolares |
| Académico           | ades_tareas, ades_tareas_entregas, ades_asistencias, ades_clases      |
| Evaluaciones        | ades_evaluaciones, ades_calificaciones_evaluaciones                    |
| **Gradebook**       | **ades_esquemas_ponderacion, ades_items_ponderacion**                 |
|                     | **ades_calificaciones_periodo** (extendida con score_por_item, cerrada)|
|                     | **calcular_calificacion_periodo()** — función PL/pgSQL + 3 triggers   |
| Badges              | ades_badges, ades_badge_otorgados                                     |
| Comunicación        | ades_comunicados, ades_notificaciones, ades_encuestas                 |
| IA                  | ades_ai_conversaciones, ades_alertas_academicas, ades_learning_paths  |
| Reportes            | ades_certificados, ades_reportes_academicos, ades_reportes_conducta   |
| BI                  | schema ades_bi — 5 vistas materializadas                              |

---

## 🔑 Patrones de Código Críticos

### JWT → usuario
```python
sub = current_user.get("sub")
user_row = await db.execute(text("SELECT id FROM ades_usuarios WHERE oidc_sub = :s"), {"s": sub})
user_id = user_row.scalar()
```

### SQL async
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

### Gradebook — cálculo automático
La función `calcular_calificacion_periodo(alumno_id, grupo_id, materia_id, periodo_id)`
se dispara automáticamente por triggers AFTER INSERT/UPDATE en:
- `ades_tareas_entregas` (estatus_entrega, calificacion_obtenida)
- `ades_calificaciones_evaluaciones` (calificacion)
- `ades_asistencias` (estatus_asistencia)

Herencia de esquema: materia-específico → nivel-genérico (selección por ORDER BY).

---

## 📐 Reglas de Negocio Inmutables

| Regla                        | Detalle                                                       |
|------------------------------|---------------------------------------------------------------|
| Escalas SEP                  | 0–10, mínimo aprobatorio 6.0                                  |
| Escalas UAEMEX               | 0–100, mínimo aprobatorio 60                                  |
| Suma ponderaciones           | Siempre 100% (validado en POST/PUT esquemas)                  |
| Calificación cerrada         | Solo ADMIN puede modificarla                                  |
| Ajuste manual                | Requiere justificación ≥ 20 caracteres                        |
| Idempotencia triggers        | `calcular_calificacion_periodo` es idempotente (UPSERT)       |
| Archivos de entrega          | Siempre a MinIO bucket `tareas-entregas`; nunca al filesystem |
| No eliminar entregas         | Usar estatus SIN_ENTREGA o EXCUSA                             |
