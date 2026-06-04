# ADES — Administración Escolar Instituto Nevadi

[![Estado](https://img.shields.io/badge/Estado-FASES%201--10%20Completas-brightgreen)](https://github.com/imarthe75/ades-nevadi)
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
| **Grupos** | 78 (66 activos + 12 futuros) |
| **Profesores** | 168 |
| **Alumnos** | 1,980 |
| **Materias en plan** | 66 (7 primaria · 11 secundaria · 48 preparatoria) |
| **Temas curriculares** | 600+ |
| **Operaciones REST** | 175 (FASES 1–10) |
| **Componentes Angular** | 27 (lazy-loaded) |
| **Tablas PostgreSQL** | 69 |
| **Migraciones DDL** | 7 (001–007) |

---

## Arquitectura Técnica

### Stack Tecnológico

| Capa | Tecnología | Versión | Propósito |
|------|-----------|---------|-----------|
| **Base de Datos** | PostgreSQL + pgvector | 18 | Persistencia ACID, embeddings semánticos, auditoría |
| **PKs** | UUID v7 (`uuidv7()`) | nativo PG18 | Time-ordered, sin fragmentación de índice B-tree |
| **Caché / Sesiones** | Valkey | 9.1.0 | Sessions, cola Celery, memoria corta del agente |
| **Almacenamiento** | MinIO | latest | Compatible S3 — archivos de tareas y entregas |
| **Autenticación IdP** | Authentik | 2026.5.2 | OIDC/OAuth2, Google Workspace SSO, cuentas locales |
| **Backend API** | FastAPI | 0.136+ | Asincrónico, Pydantic, OpenAPI docs |
| **Runtime** | Python | 3.12 | LTS |
| **Frontend SPA** | Angular + PrimeNG | 22 | Framework reactivo, UI empresarial |
| **Tareas Async** | Celery + Valkey | 5.6+ | Background jobs, reportes, notificaciones |
| **BI / Reportes** | Apache Superset | 6.1.0 | Dashboards interactivos, KPIs en tiempo real |
| **Horarios** | aSc TimeTables | latest | Motor K-12 especializado, import/export XML |
| **Agente IA** | LangChain + LangGraph + Claude | latest | Asistente pedagógico, riesgo académico |
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
| 6 | **Usuarios y RBAC** — 14 roles, OIDC Authentik, SSO, cuentas locales | ✅ |

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

| Dominio | Servicio | Cert (expira) |
|---------|---------|---------------|
| `ades.setag.mx` | Frontend Angular + API ADES | 2026-09-01 |
| `auth.ades.setag.mx` | Authentik IdP | 2026-09-01 |
| `bi.ades.setag.mx` | Apache Superset | 2026-09-01 |
| `minio.ades.setag.mx` | MinIO consola admin | 2026-09-01 |

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

1. **Primaria:** 1 titular por grupo para TODAS las materias EXCEPTO inglés. 1 profesor de inglés por plantel (cubre 12 grupos por plantel).
2. **Secundaria / Preparatoria:** 1 profesor por materia por grupo.
3. **Grupos:** siempre A y B. Máximo 2 por grado salvo instrucción explícita.
4. **Ixtapan:** Secundaria completa con 3 grados (antes solo 1° y 2°). Sin preparatoria.
5. **Tenancingo prep:** Semestres 1-2 activos ciclo 26B; sem 3-6 `is_active=FALSE` (futuros).
6. **Metepec prep:** Semestres 1-4 activos; sem 5-6 `is_active=FALSE` (futuros).
7. **Calendario SEP:** aplica a primaria y secundaria de los 3 planteles.
8. **Calendario UAEMEX:** aplica solo a Metepec y Tenancingo preparatoria.
9. **Fechas en UI:** DD-MM-YYYY. Idioma: español.
10. **Auth personal:** `clave_hash` con hash Argon2id + `oidc_sub` del JWT Authentik.

---

## Roles del Sistema

| Rol | Alcance | Auth actual |
|-----|---------|-------------|
| `ADMIN_GLOBAL` | Todos los planteles y niveles | Authentik local → Google SSO cuando disponible |
| `ADMIN_PLANTEL` | Un plantel completo | Idem |
| `DIRECTOR` | Un plantel | Idem |
| `COORDINADOR_ACADEMICO` | Un nivel dentro de un plantel | Idem |
| `DOCENTE` | Sus grupos y materias asignadas | Authentik local |
| `MEDICO_ESCOLAR` | Expedientes médicos de su plantel | Authentik local |
| `ALUMNO` | Su propio expediente y materias | Cuenta local Authentik |
| `PADRE_FAMILIA` | Expedientes de sus hijos | Cuenta local Authentik |

> Google Workspace SSO para `@institutonevadi.edu.mx` está configurado en Authentik pero pendiente de credenciales de Google Cloud Console. Todos usan cuentas locales Authentik hasta entonces.

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

## Roadmap

| Período | Fase | Objetivos principales |
|---------|------|----------------------|
| **Q3 2026** | FASE 1 | MVP: inscripciones, profesores, RBAC, API REST base |
| **Q4 2026** | FASE 2 | Calificaciones, Quiz Engine, Asistencias, Tareas, Notificaciones |
| **Q1 2027** | FASE 3 | Horarios aSc, Expediente Médico, Boletas PDF, Evaluación Docente, Badges |
| **Q2 2027** | FASE 4 | Asistente IA, Riesgo Académico, Learning Paths |
| **Q3 2027** | FASE 4+ | Dashboard BI ClickHouse, pipeline CDC, Análisis de Patrones |

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
