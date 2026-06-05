# ADES — Administración Escolar Instituto Nevadi

[![Estado](https://img.shields.io/badge/Estado-FASES%201--26%20Roadmap-brightgreen)](https://github.com/imarthe75/ades-nevadi)
[![Python](https://img.shields.io/badge/Python-3.12-brightgreen)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.136+-blue)](https://fastapi.tiangolo.com/)
[![Angular](https://img.shields.io/badge/Angular-22-red)](https://angular.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)](https://www.docker.com/)
[![Licencia](https://img.shields.io/badge/Licencia-Privado-red)](#licencia)

## Descripción General

**ADES** es un sistema integral de administración escolar diseñado específicamente para el **Instituto Nevadi** (México), institución educativa privada con 3 planteles y 3 niveles: Primaria (SEP), Secundaria (SEP) y Preparatoria (UAEMEX).

Gestiona la operación completa: estructura académica, inscripciones, asignación docente, calificaciones, asistencias, comunicación con padres, expediente médico, conducta estudiantil, horarios, y análisis predictivo de riesgo académico mediante IA.

**Contexto Institucional:**

| Campo | Valor |
|-------|-------|
| **Razón social** | Instituto Nevadi |
| **Eslogan** | "EL ÚNICO CAMINO PARA SALIR ADELANTE ES LA EDUCACIÓN" |
| **Sitio web** | https://institutonevadi.edu.mx/ |
| **Planteles** | Metepec · Tenancingo · Ixtapan de la Sal (Edomex) |
| **Ciclo SEP** | 2026-2027 (Primaria y Secundaria) |
| **Ciclo UAEMEX** | 26B / 27A (Preparatoria) |

### Estadísticas del Ciclo 2026-2027

| Métrica | Valor |
|---------|-------|
| **Planteles** | 3 |
| **Grados** | 39 |
| **Grupos activos** | 66 |
| **Profesores** | 168 |
| **Alumnos** | 1,980 |
| **Usuarios totales** | 3,483 (alumnos + padres + personal) |
| **Materias en plan** | 63 (7 primaria NEM · 5 secundaria NEM · 51 preparatoria CBU 2024) |
| **Calificaciones** | 76,320 |
| **Asistencias** | 180,000+ |
| **Tareas** | 9,600 |
| **Tablas PostgreSQL** | 89 |
| **Migraciones DDL** | 15 (001–015) |
| **Roles del sistema** | 18 |
| **Módulos Angular** | 35+ (lazy-loaded) |

---

## Arquitectura Técnica

### Stack Tecnológico

| Capa | Tecnología | Versión | Propósito |
|------|-----------|---------|-----------|
| **Base de Datos** | PostgreSQL + pgvector | 18 | Persistencia ACID, embeddings semánticos, auditoría |
| **Push Notifications** | ntfy | latest | Notificaciones push nativas sin Firebase, SSE al browser/móvil |
| **PDF Avanzado** | Stirling-PDF | latest | Merge, marca de agua, OCR, compresión — complemento de Carbone |
| **Monitoreo** | Grafana + Prometheus | latest | Dashboards de latencia/uptime, métricas de todos los servicios |
| **Automatización** | n8n | latest | Workflows: alertas académicas, batch boletas, notificaciones automáticas |
| **PKs** | UUID v7 (`uuidv7()`) | nativo PG18 | Time-ordered, sin fragmentación de índice B-tree |
| **Caché / Sesiones** | Valkey | 9.1.0 | Sessions, cola Celery, memoria corta del agente |
| **Almacenamiento** | MinIO | latest | Compatible S3 — archivos de tareas y entregas |
| **Autenticación IdP** | Authentik | 2026.5.2 | OIDC/OAuth2, Google Workspace SSO, cuentas locales |
| **Backend API** | FastAPI | 0.136+ | Asincrónico, Pydantic, OpenAPI docs |
| **Runtime** | Python | 3.12 | LTS |
| **Frontend SPA** | Angular + PrimeNG | 22 | Framework reactivo, UI empresarial |
| **Tareas Async** | Celery + Valkey | 5.6+ | Background jobs, reportes, notificaciones |
| **BI / Dashboards** | Apache Superset | 6.1.0 | Dashboards interactivos, KPIs en tiempo real, iframe embebido |
| **Generador Reportes** | Carbone | latest | Plantillas DOCX/XLSX → PDF, boletas, constancias, kardex |
| **AI Chatbot** | Flowise + Claude Haiku | latest | NL→SQL, chatbot pedagógico con RLS por rol |
| **Horarios** | aSc TimeTables | latest | Motor K-12 especializado, import/export XML |
| **Agente IA** | LangChain + LangGraph + Claude | latest | Asistente pedagógico, riesgo académico, NL→SQL |
| **Reverse Proxy** | Nginx | alpine | TLS/SSL, enrutamiento |
| **Contenedores** | Docker + Compose | 29+ / 5+ | Reproducibilidad, orquestación local |
| **OS** | Ubuntu Server | 24 LTS | ARM64 (OCI Always Free) o x86_64 |

### Diagrama de Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                  Cliente (Browser)                           │
│             Angular SPA + PrimeNG UI                         │
└──────────────┬──────────────────────────────────────────────┘
               │ HTTPS / OIDC
┌──────────────▼──────────────────────────────────────────────┐
│             Nginx (Reverse Proxy · TLS · 4 dominios)         │
│  ades.setag.mx · auth.ades.setag.mx · bi.ades.setag.mx      │
│  minio.ades.setag.mx                                         │
└──────────────┬──────────────────────────────────────────────┘
               │ HTTP
┌──────────────▼──────────────────────────────────────────────┐
│            FastAPI Backend (API REST + Celery Workers)        │
│  Módulos: Académica · Inscripciones · Calificaciones          │
│           Asistencias · Tareas · Comunicados · Horarios       │
│           Expediente Médico · Conducta · Reportes · IA        │
└──────┬──────────────┬───────────────────┬────────────────────┘
       │              │                   │
┌──────▼──────┐  ┌────▼────────┐  ┌──────▼──────┐
│ PostgreSQL  │  │  Valkey     │  │   MinIO     │
│ 18 + pgvec  │  │  9.1.0      │  │  (S3-compat)│
│ UUID v7 PKs │  │  Sessions   │  │  Archivos   │
│ 57 tablas   │  │  Celery     │  │  Entregas   │
└─────────────┘  └─────────────┘  └─────────────┘
┌───────────────────────────┐  ┌──────────────────────────────┐
│ Authentik 2026.5.2 (IdP)  │  │ Superset 6.1.0 (BI)          │
│ OIDC: ades-frontend,      │  │ KPIs · Tendencias            │
│        superset            │  │ Reportes ejecutivos          │
│ Google SSO (pendiente)    │  └──────────────────────────────┘
└───────────────────────────┘
┌───────────────────────────┐  ┌──────────────────────────────┐
│ aSc TimeTables (Horarios) │  │ Claude API + LangChain (IA)  │
│ Export/Import XML K-12    │  │ Asistente · Rúbricas         │
└───────────────────────────┘  │ Riesgo académico predictivo  │
                               └──────────────────────────────┘
```

---

## Dominios del Sistema (DDD)

| Dominio | Entidades principales |
|---------|----------------------|
| **1. Identidad Institucional** | Instituto, Planteles, Contactos, Identidad visual, Catálogo SEPOMEX |
| **2. Estructura Académica** | Niveles, Grados, Grupos, Materias, Planes, Temas, Ciclos escolares |
| **3. Población Escolar** | Alumnos, Padres/tutores, Profesores, Inscripciones |
| **4. Operación Académica** | Calendario, Horarios, Asistencias, Calificaciones, Evaluaciones, Tareas |
| **5. Comunicación** | Comunicados, Acuses digitales, Notificaciones, Foros por materia |
| **6. Salud y Bienestar** | Expediente médico, Incidentes, Alergias/medicamentos, Contacto emergencia |
| **7. Conducta y Disciplina** | Reportes, Incidentes, Planes de mejora, Historial disciplinario |
| **8. Reportes y Analítica** | Boletas PDF, KPIs, Dashboards BI, Predicción de riesgo |
| **9. Inteligencia Artificial** | Asistente pedagógico, Rúbricas automáticas, Learning paths adaptativas |

---

## Módulos Implementados por Fase

### FASE 1 — Core ✅

| # | Módulo | Estado |
|---|--------|--------|
| 1 | **Identidad Institucional** — datos, logo, colores, catálogo SEPOMEX | ✅ |
| 2 | **Estructura Académica** — niveles, grados, grupos, ciclos, calendario | ✅ |
| 3 | **Planes de Estudio** — materias, temas, carga horaria SEP/UAEMEX | ✅ |
| 4 | **Inscripciones y Alumnos** — alta, filiación, inscripción, histórico | ✅ |
| 5 | **Profesores** — registro, asignación materia↔grupo (reglas SEP) | ✅ |
| 6 | **Usuarios y RBAC** — 18 roles, OIDC Authentik, SSO, cuentas locales | ✅ |

### FASE 2 — Operación Académica ✅

| # | Módulo |
|---|--------|
| 1 | **Calificaciones** — libreta bimestral/parcial, boleta PDF, edición inline |
| 2 | **Asistencias** — por clase, ausentismo, alertas académicas |
| 3 | **Tareas y Entregas** — subida MinIO, calificación, rúbricas |
| 4 | **Clases** — registro de clases por grupo/materia |

### FASE 3 — Módulos Especializados ✅

| # | Módulo |
|---|--------|
| 1 | **Horarios vía aSc TimeTables** — export/import XML, grid semanal |
| 2 | **Expediente Médico** — alergias, medicamentos, incidentes |
| 3 | **Reportes de Conducta** — incidentes, seguimiento, compromisos |
| 4 | **Boletas PDF** — WeasyPrint + Jinja2, template institucional |
| 5 | **Evaluación Docente 360°** — criterios ponderados, promedio global |

### FASE 4 — IA + Analytics ✅

| # | Módulo |
|---|--------|
| 1 | **Asistente Pedagógico IA** — chat con Claude Sonnet 4.6, sugerencias contextuales |
| 2 | **Alertas de Riesgo Académico** — detección automática (<6.0, ausentismo <80%) |
| 3 | **Learning Paths** — rutas de refuerzo adaptativas, progreso por alumno |
| 4 | **Grade Analytics** — tendencias, distribución, riesgo, resumen ejecutivo |
| 5 | **Dashboard BI** — Apache Superset + 5 vistas materializadas en schema `ades_bi` |

### FASE 5 — Comunicación ✅

| # | Módulo |
|---|--------|
| 1 | **Comunicados y Circulares** — rich text, acuse digital, tipos OFICIAL/INFO/URGENTE |
| 2 | **Notificaciones in-app** — campanita en topbar, badge conteo, marcar leída |

### FASE 6 — Evaluaciones + Planeación + Certificados ✅

| # | Módulo |
|---|--------|
| 1 | **Evaluaciones** — agenda de exámenes ORDINARIO/FINAL/EXTRAORDINARIO |
| 2 | **Planeación de Clases** — temas PLANEADO/IMPARTIDO/PENDIENTE, cobertura |
| 3 | **Rúbricas** — CRUD criterios con niveles_logro JSONB, ponderación |
| 4 | **Certificados Digitales** — folio único verificable, PDF firmado |

### FASE 7 — Encuestas ✅

| # | Módulo |
|---|--------|
| 1 | **Encuestas y Clima Escolar** — ESCALA_5, OPCION_MULTIPLE, BOOLEANO, TEXTO_LIBRE |

### FASE 8 — Badges y Gamificación ✅

| # | Módulo |
|---|--------|
| 1 | **Insignias** — catálogo con 8 seeds, auto-evaluación por métricas (asistencia/promedio/conducta), otorgamiento manual |

### FASE 9 — Portal del Alumno ✅

| # | Módulo |
|---|--------|
| 1 | **Portal 360°** — búsqueda, KPIs, alertas, pivot de calificaciones, asistencias, tareas, badges, LP |

### FASE 10 — Gradebook Curricular Integrado ✅


| # | Módulo | Detalle |
|---|--------|---------|
| 1 | **Gradebook** (profesor) | Panel spreadsheet: actividades × alumnos, drawer de calificación, ajuste manual con justificación, cobertura curricular |
| 2 | **Mi Progreso** (alumno) | Cards por materia con % + desglose por ítem, tareas pendientes con countdown, subida de archivos a MinIO |
| 3 | **Ponderaciones** (admin) | Esquemas por nivel/materia, validación suma=100%, historial de versiones |
| 4 | **Cálculo automático** (PG) | Función `calcular_calificacion_periodo()` con 3 triggers — idempotente, escala dinámica SEP/UAEMEX |

### Migración 008 — Ampliación de personal ✅

| # | Cambio | Detalle |
|---|--------|---------|
| 1 | **4 roles nuevos** | TUTOR, APOYO_ACADEMICO, APOYO_ADMINISTRATIVO, COORDINADOR_AREA |
| 2 | **DIRECTOR ampliado** | Puede ser por nivel educativo dentro del plantel — hasta 3 directores por plantel |
| 3 | **Tabla `ades_areas_academicas`** | 8 áreas globales: Matemáticas, Español, Inglés, Ciencias, Historia, Cívica, Ed. Física, Tecnología |
| 4 | **Tabla `ades_coordinaciones_area`** | Asigna COORDINADOR_AREA a un área global (transversal a planteles) |
| 5 | **Restricción eliminada** | ~~1 profesor de inglés por plantel~~ — sin límite de docentes por materia |

---

## Inspiración en Moodle

Análisis de [github.com/moodle/moodle](https://github.com/moodle/moodle) — módulos seleccionados para ADES:

### Por qué ADES no adopta Moodle completo

| Área | Moodle | ADES |
|------|--------|------|
| **Tipo** | LMS (Learning Management) | SIS (School Information System) |
| **Multi-plantel** | Single-tenant | 3 planteles, 2 autoridades (SEP/UAEMEX) |
| **Horarios** | Básico | aSc TimeTables (especializado K-12) |
| **Regulaciones** | Genéricas | SEP/UAEMEX específicas México |
| **Stack** | PHP/MySQL | FastAPI/PostgreSQL 18/Angular |
| **Soberanía** | Cloud-dependent | Todo local (gitops, self-hosted) |
| **IA** | Módulos básicos | Claude API + LangChain state-of-the-art |

---

## Base de Datos

### Convenciones de Diseño

| Convención | Valor |
|-----------|-------|
| **Prefijo tablas** | `ades_` |
| **Primary Key** | `id UUID NOT NULL DEFAULT uuidv7()` — time-ordered, sin fragmentación |
| **Business Key** | `ref UUID NOT NULL UNIQUE DEFAULT uuidv7()` — para integraciones externas |
| **Auditoría** | `auditoria.trg_auditoria_biu` BEFORE INSERT/UPDATE en todas las tablas |
| **Soft delete** | `is_active BOOLEAN NOT NULL DEFAULT TRUE` |
| **Estatus** | FK `estatus_id UUID → ades_estatus(id)` |
| **Nombres** | snake_case, español, descriptivos |
| **FK columns** | sufijo `_id UUID REFERENCES tabla(id)` |
| **Concurrencia** | `row_version INTEGER DEFAULT 1` (optimistic locking) |

### Estructura de UUID v7

Los IDs son time-ordered:
```
019e8f74-d142-7c91-8b82-c84464113dad   ← Metepec
019e8f74-d143-7368-a0b6-06cc2fbc7156   ← Tenancingo
019e8f74-d143-740c-aa16-63a83c575d92   ← Ixtapan
```
Los primeros 48 bits codifican el timestamp (ms), lo que garantiza orden de inserción sin fragmentar el índice B-tree.

### Tablas (57 en total)

```
── Institucional ─────────────────────────────────────────────────
ades_escuelas              ades_planteles          ades_paises
ades_estados               ades_municipios         ades_localidades
ades_codigos_postales      ades_tipos_asentamiento ades_identidad_institucional
ades_historico_identidad   ades_informacion_escuela

── Académica ─────────────────────────────────────────────────────
ades_niveles_educativos    ades_grados             ades_grupos
ades_plantel_niveles       ades_ciclos_escolares   ades_periodos_evaluacion
ades_calendario_escolar    ades_materias           ades_materias_plan
ades_temas                 ades_aulas

── Personas ──────────────────────────────────────────────────────
ades_personas              ades_profesores         ades_estudiantes
ades_inscripciones         ades_contactos_familiares ades_telefonos
ades_correos_electronicos  ades_direcciones        ades_asignaciones_docentes
ades_disponibilidad_docente

── Operación ─────────────────────────────────────────────────────
ades_asistencias           ades_calificaciones_periodo ades_evaluaciones
ades_calificaciones_evaluaciones ades_tareas        ades_tareas_entregas
ades_clases                ades_horarios           ades_planeacion_clases
ades_avance_planificacion  ades_archivos

── Comunicación ──────────────────────────────────────────────────
ades_comunicados           ades_acuses_comunicado  ades_notificaciones

── Usuarios y Seguridad ──────────────────────────────────────────
ades_usuarios              ades_roles              ades_estatus

── Salud y Conducta ──────────────────────────────────────────────
ades_expedientes_medicos   ades_incidentes_medicos ades_personal_salud
ades_reportes_conducta

── Reportes y BI ─────────────────────────────────────────────────
ades_rubricas              ades_rubrica_criterios  ades_reportes_academicos

── Auditoría ─────────────────────────────────────────────────────
auditoria.bitacora         (+ vistas de resumen)
```

---

## Configuración de Subdominios y TLS

| Dominio | Servicio | Cert (expira) | Acceso |
|---------|---------|---------------|--------|
| `ades.setag.mx` | Frontend Angular + API ADES | 2026-09-01 | Público (con auth) |
| `auth.ades.setag.mx` | Authentik IdP | 2026-09-01 | Público |
| `bi.ades.setag.mx` | Apache Superset | 2026-09-01 | Admin / Docentes |
| `minio.ades.setag.mx` | MinIO consola admin | 2026-09-01 | Admin |
| `notify.ades.setag.mx` | ntfy push notifications | 2026-09-03 | Público — app móvil y browser SSE (FASE 20) |
| `monitor.ades.setag.mx` | Grafana dashboards | 2026-09-03 | Admin — restringir por IP en producción (FASE 22) |

Certificados Let's Encrypt renovados automáticamente vía `certbot` del sistema (no via Docker).

---

## Instalación

### Requisitos

- Docker 29+ con Compose v2
- Ubuntu 24 LTS (ARM64 recomendado — OCI Always Free)
- RAM: 24 GB mínimo
- Almacenamiento: 100 GB en `/opt/ades`
- Dominio con DNS configurado al servidor

### Pasos rápidos

```bash
# 1. Clonar
sudo mkdir -p /opt/ades && sudo chown $(id -u):$(id -g) /opt/ades
git clone https://github.com/imarthe75/ades-nevadi.git /opt/ades
cd /opt/ades

# 2. Configurar variables
cp .env.example .env && nano .env

# 3. Red compartida
docker network create aura-network || true

# 4. Certificados TLS
sudo certbot certonly --standalone -d ades.setag.mx \
  -d auth.ades.setag.mx -d bi.ades.setag.mx -d minio.ades.setag.mx \
  --email admin@setag.mx --agree-tos --non-interactive

# 5. Infraestructura base
docker compose up -d postgres valkey minio
sleep 15

# 6. Inicializar BD (schema UUID v7 + seeds)
bash db/scripts/reset_and_reseed.sh

# 7. Stack completo
docker compose up -d

# 8. Configurar Authentik
AUTHENTIK_BOOTSTRAP_TOKEN=$(grep AUTHENTIK_BOOTSTRAP_TOKEN .env | cut -d= -f2) \
  python3 infrastructure/authentik/setup.py
```

### Variables críticas en `.env`

```bash
POSTGRES_PASSWORD=<seguro>
AUTHENTIK_SECRET_KEY=$(openssl rand -hex 32)
AUTHENTIK_BOOTSTRAP_PASSWORD=<seguro>
AUTHENTIK_BOOTSTRAP_TOKEN=$(openssl rand -hex 32)
VALKEY_PASSWORD=<seguro>
MINIO_ROOT_PASSWORD=<seguro>
SUPERSET_SECRET_KEY=$(openssl rand -hex 32)
# Google SSO (opcional — cuando esté disponible)
# GOOGLE_OAUTH_CLIENT_ID=<id>.apps.googleusercontent.com
# GOOGLE_OAUTH_CLIENT_SECRET=<secret>
```

---

## Reglas de Negocio Críticas

1. **Primaria:** 1 titular por grupo para todas las materias. **Un plantel puede tener múltiples docentes de la misma materia (incluyendo inglés).** No hay restricción de unicidad por asignatura.
2. **Secundaria / Preparatoria:** uno o más profesores por materia por plantel, asignados individualmente a cada grupo.
3. **Grupos:** siempre A y B. Máximo 2 por grado salvo instrucción explícita.
4. **Ixtapan:** Secundaria completa con 3 grados (antes solo 1° y 2°). Sin preparatoria.
5. **Tenancingo prep:** Semestres 1-2 activos ciclo 26B; sem 3-6 `is_active=FALSE` (futuros).
6. **Metepec prep:** Semestres 1-4 activos; sem 5-6 `is_active=FALSE` (futuros).
7. **Calendario SEP:** aplica a primaria y secundaria de los 3 planteles.
8. **Calendario UAEMEX:** aplica solo a Metepec y Tenancingo preparatoria.
9. **Fechas en UI:** DD-MM-YYYY. Idioma: español.
10. **Auth personal:** `clave_hash` con hash Argon2id + `oidc_sub` del JWT Authentik.

---

## Roles del Sistema (18 roles)

| Nivel | Rol | Alcance |
|:-----:|-----|---------|
| 0 | `ADMIN_GLOBAL` | Todos los planteles y niveles |
| 1 | `ADMIN_PLANTEL` | Un plantel completo |
| 2 | `DIRECTOR` | Por nivel educativo dentro del plantel (hasta 3 por plantel) |
| 2 | `SUBDIRECTOR` | Suplente del director |
| 2 | `COORDINADOR_ADMINISTRATIVO` | Procesos administrativos por plantel/nivel |
| 2 | `COORDINADOR_RH` | Personal docente y administrativo |
| 2 | `COORDINADOR_AREA` | **Global** — coordina un área académica (Matemáticas, Inglés…) en todos los planteles |
| 3 | `COORDINADOR_ACADEMICO` | Coordinación académica por nivel dentro del plantel |
| 3 | `TUTOR` | Seguimiento académico personalizado de un grupo de estudiantes |
| 3 | `ORIENTADOR` | Orientación educativa y vocacional (sec/prep) |
| 3 | `SECRETARIA_ACADEMICA` | Expedientes, certificados, actas |
| 4 | `DOCENTE` | Sus grupos y materias asignadas |
| 4 | `MEDICO_ESCOLAR` | Expedientes médicos de su plantel |
| 4 | `PREFECTO` | Disciplina, supervisión |
| 4 | `APOYO_ACADEMICO` | Recursos, biblioteca, laboratorio |
| 4 | `APOYO_ADMINISTRATIVO` | Trámites, archivo, atención |
| 5 | `ALUMNO` | Su propio expediente y materias |
| 5 | `PADRE_FAMILIA` | Expedientes de sus hijos |

> **Google Workspace SSO** para `@institutonevadi.edu.mx` configurado en Authentik — pendiente de credenciales Google Cloud Console. Personal usa cuentas locales Authentik hasta entonces.

---

## Estructura del Repositorio

```
/opt/ades/
├── .agent/                         # Contexto cognitivo del agente residente
│   ├── CONTEXT.md                  # Especificación completa del sistema
│   ├── STATE.md                    # Estado actual (actualizado por sesión)
│   └── AGENT.md                    # Leyes operacionales
│
├── DECISIONS/                      # Architecture Decision Records (ADRs)
│
├── db/
│   ├── migrations/
│   │   └── 001_initial_schema.sql  # DDL: 57 tablas, UUID v7, auditoría
│   ├── seeds/
│   │   ├── 001_datos_base.sql      # Planteles, niveles, grados, materias, ciclos
│   │   ├── 002_grupos_profesores_v4.sql  # 78 grupos, 168 profesores, asignaciones
│   │   ├── 003_alumnos_padres_v4.sql    # 1980 alumnos, padres, inscripciones, usuarios
│   │   ├── 004_plan_estudios.sql   # Materias por plan, temas (600+), rúbricas
│   │   └── 005_disponibilidad_aulas.sql # Aulas y disponibilidad para aSc
│   └── scripts/
│       ├── init_multi_db.sh        # Crea BDs authentik y superset
│       └── reset_and_reseed.sh     # Drop + recrear ades + cargar seeds
│
├── backend/                        # FastAPI (en desarrollo FASE 1)
│   ├── app/
│   │   ├── api/                    # Routers por módulo
│   │   ├── models/                 # SQLAlchemy ORM
│   │   ├── schemas/                # Pydantic DTOs
│   │   ├── services/               # Lógica de negocio
│   │   └── worker/                 # Celery tasks
│   └── alembic/                    # Migraciones incrementales
│
├── frontend/                       # Angular + PrimeNG (en desarrollo FASE 1)
│
├── integrations/
│   ├── superset/
│   │   └── Dockerfile              # Superset 6.1.0 + psycopg2-binary + redis
│   ├── asc_horarios/               # Export/import XML para aSc TimeTables
│   └── cube/                       # Capa semántica Cube.js (FASE 4)
│
├── infrastructure/
│   ├── nginx/
│   │   └── nginx.conf              # Reverse proxy: 4 dominios + TLS
│   └── authentik/
│       └── setup.py                # Setup automatizado vía API Authentik
│
├── docker-compose.yml              # Stack completo
├── .env.example                    # Template de variables
├── .env                            # Valores reales (gitignored)
└── README.md                       # Este archivo
```

---

## Monitoreo y Mantenimiento

### Health Checks

```bash
docker compose ps                                     # Estado de todos los servicios
docker compose exec postgres pg_isready -U ades_admin # BD
docker compose exec valkey valkey-cli ping            # Valkey
curl -sI https://ades.setag.mx/                       # Frontend
curl -sI https://auth.ades.setag.mx/                  # Authentik
```

### Backup

```bash
# Manual
docker compose exec -T postgres pg_dump -U ades_admin ades \
  > backup_$(date +%F_%H%M).sql

# Reset completo (¡DESTRUCTIVO! solo en desarrollo)
bash db/scripts/reset_and_reseed.sh
```

### Logs de Auditoría

```bash
docker compose exec postgres psql -U ades_admin -d ades -c "
  SELECT tabla, usuario_modificacion, fcmodificacion, row_version
  FROM ades_personas ORDER BY fcmodificacion DESC LIMIT 10;"
```

---

## Solución de Problemas

| Síntoma | Causa | Solución |
|---------|-------|----------|
| Nginx no arranca: `cannot load certificate` | Cert en host, no en volumen Docker | Usar bind mount `/etc/letsencrypt:/etc/letsencrypt:ro` |
| Authentik: `Secret key missing` | Typo `AUTHENTIK_SECRET__KEY` (doble `_`) | Corregir a `AUTHENTIK_SECRET_KEY` en compose |
| JS Authentik: MIME `text/plain` | Falta `include /etc/nginx/mime.types` | Agregar al bloque `http {}` del nginx.conf |
| WebSocket Authentik falla | `/ws/` no proxeado | Agregar `location /ws/ { proxy_pass ... }` por dominio |
| BD tiene BIGINT en lugar de UUID | Schema desincronizado | `bash db/scripts/reset_and_reseed.sh` |
| Grupos prep con `is_active=FALSE` | Diseño intencional | Se activan ciclo a ciclo con `UPDATE ades_grupos SET is_active=TRUE WHERE ...` |

---

## Progreso y Roadmap

> **Estado actual (Junio 2026):** FASES 1–10 completadas. El sistema está operacional y desplegado en producción. El desarrollo avanzó aproximadamente **18 meses antes del plan original.**

### Completado ✅

| Período | Fase | Logros |
|---------|------|--------|
| **Jun 2026** | FASES 1–10 | Sistema base: 175 endpoints REST, Gradebook PG, IA, Badges, Portal alumno, Superset BI |
| **Jun 2026** | FASES 11–13 | RBAC, módulo Admin, Manual de usuario, HelpButton |
| **Jun 2026** | Mig 011–015 | Expediente médico, contactos familiares, CBU 2024 UAEMEX, NEM 2022 |
| **Jun 2026** | Seeds completos | 3,483 usuarios, 76,320 calificaciones, 180k asistencias, 9,600 tareas |
| **Jun 2026** | Portal Padres | Módulo /padres con KPIs, calificaciones y asistencia por alumno |
| **Jun 2026** | Optimización | Paginación server-side, índices PostgreSQL, debounce en búsquedas |
| **Jun 2026** | Planes de Estudio | Mapa curricular visual CBU 2024 + NEM, CRUD de materias |
| **Jun 2026** | FASE 15 — Auditoría | Middleware FastAPI → `ades_audit_log`, tab Auditoría en Admin |
| **Jun 2026** | FASE 16 — Superset BI | OIDC con Authentik, `custom_sso_security_manager`, guest tokens, componente Angular `/bi` |
| **Jun 2026** | FASE 17 — AI Chatbot | Flowise (port 3002) + NL→SQL con Claude Haiku, tab "Consulta de datos" en módulo IA |
| **Jun 2026** | FASE 18 — Carbone Reportes | Microservicio Node.js (port 3001), endpoints FastAPI, módulo Angular `/reportes` con gestor de plantillas |
| **Jun 2026** | FASE 19 — Planes y Programas | CRUD materias + plan-estudio completo, mapa inline-edit, temario por materia, drawer con estadísticas |
| **Jun 2026** | FASE 20 — ntfy Push | `ades-ntfy` (port 2586), `PushNotificationService` Angular SSE, notificaciones nativas del browser |
| **Jun 2026** | FASE 21 — Stirling-PDF | `ades-stirling-pdf` (port 8081), endpoints fusión/marca-agua/compresión/boletas-grupo |
| **Jun 2026** | FASE 22 — Grafana+Prometheus | `ades-prometheus` (9090) + `ades-grafana` (3003), métricas FastAPI, dashboard ADES API, /monitor Angular |
| **Jun 2026** | FASE 23 — n8n Automatización | `ades-n8n` (5678) + BD PostgreSQL, webhooks FastAPI asistencia/calificación/comunicado/cierre-periodo |

### Pendiente de configuración

| Estimado | Tarea | Bloqueante |
|----------|-------|------------|
| **Jul 2026** | Google Workspace SSO (FASE 14) | Credenciales Google Cloud Console del Instituto |
| **Jul 2026** | Superset primer arranque | Ejecutar `infrastructure/superset/init.sh`, crear datasource `ades_bi` en UI |
| **Jul 2026** | Flowise — configurar chatflow | UI en `localhost:3002`, conectar herramienta SQL al backend ADES, copiar UUID al `.env` |
| **Jul 2026** | Chatbot NL→SQL — habilitar Claude | Agregar `ANTHROPIC_API_KEY` al `.env` y reconstruir imagen |
| **Q3 2026** | FASE 27 — HashiCorp Vault | Gestión centralizada de secretos, credenciales DB dinámicas, rotación automática, audit trail |
| **Q3 2026** | FASE 28 — Firma Digital (pyhanko) | Firma PAdES de boletas/certificados con llave institucional + integración futura FIEL/SAT |
| **Q3 2026** | AI Chatbot (FASE 17) | Flowise + Vanna AI sobre PostgreSQL |
| **Q3 2026** | Generador de Boletas (FASE 18) | Carbone (microservicio Docker) + plantillas Word |
| **Q3 2026** | Push notifications (FASE 20) | ntfy · sin Firebase · app móvil gratuita |
| **Q3 2026** | Procesamiento PDF (FASE 21) | Stirling-PDF + Carbone |
| **Q3 2026** | Monitoreo (FASE 22) | Grafana + Prometheus |
| **Q4 2026** | Automatización flujos (FASE 23) | n8n · alertas académicas · batch boletas |
| **Q4 2026** | Expediente digital (FASE 24) | Paperless-ngx · OCR · MinIO |
| **Q4 2026** | Lanzamiento operacional completo | — |

### Extensiones aprobadas (planificadas)

#### Fases de integración institucional

| Fase | Módulo | Stack | Prioridad |
|------|--------|-------|-----------|
| 15 | Auditoría de acciones | Middleware FastAPI → `ades_audit_log` | Alta |
| 16 | Dashboards por rol | Apache Superset + iframe guest token | Alta |
| 17 | AI Chatbot (NL→SQL sobre `ades_*`) | Flowise + Vanna AI (open-source) | Media |
| 18 | Generador de boletas y reportes PDF | Carbone (microservicio Docker) | Alta |
| 19 | Módulo de planes de estudio completo | CRUD web + mapa curricular interactivo | Media |

#### Fases de automatización y productividad (aprobadas Jun 2026)

| Fase | Módulo | Stack | Impacto |
|------|--------|-------|---------|
| 20 | **Push Notifications** — alertas en tiempo real para padres y alumnos | [ntfy](https://ntfy.sh) · Docker · sin Firebase | 🔴 Alto |
| 21 | **Procesamiento PDF** — fusión boletas, marca de agua, OCR documentos | [Stirling-PDF](https://stirlingtools.com) · Docker · REST API | 🔴 Alto |
| 22 | **Monitoreo del sistema** — latencia, uptime, alertas técnicas | [Grafana](https://grafana.com) + [Prometheus](https://prometheus.io) · Docker | 🔴 Alto |
| 23 | **Automatización de flujos** — notificaciones académicas, batch boletas, recordatorios documentos | [n8n](https://n8n.io) · Docker · webhooks FastAPI | 🟡 Medio |
| 24 | **Gestión documental del expediente** — OCR de actas/CURP, búsqueda en texto, etiquetado automático | [Paperless-ngx](https://docs.paperless-ngx.com) · Docker · integra MinIO | 🟡 Medio |
| 25 | **Contenido educativo interactivo** — quizzes autocalificables, videos interactivos, juegos pedagógicos | [H5P](https://h5p.org) · Docker · xAPI → `ades_tareas` | 🟢 Bajo |
| 26 | **Videoconferencias institucionales** — reuniones padres-maestros, asesorías, clases virtuales | [BigBlueButton](https://bigbluebutton.org) · Docker · grabaciones en MinIO | 🟢 Bajo |

---

## Licencia

**Privado — Instituto Nevadi © 2026**

Sistema propietario del Instituto Nevadi. Prohibida la distribución, modificación o uso sin autorización explícita.

Desarrollado con **Claude Code** (Anthropic).

---

## Contacto

| Plantel | Email | Teléfono |
|---------|-------|----------|
| **Metepec** | nevadimetepec@institutonevadi.edu.mx | 7222971441 · 7223253683 |
| **Tenancingo** | nevaditenancingo@institutonevadi.edu.mx | 7141424323 |
| **Ixtapan de la Sal** | nevadiixtapan@institutonevadi.edu.mx | 7211433015 |

**Repositorio:** https://github.com/imarthe75/ades-nevadi  
**Última actualización:** Junio 2026 · v2.0
