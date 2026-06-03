# ADES — Administración Escolar Instituto Nevadi

Sistema integral de control escolar para el **Instituto Nevadi**, con 3 planteles y 3 niveles educativos (Primaria SEP, Secundaria SEP, Preparatoria UAEMEX).

## Planteles

| Plantel | Primaria | Secundaria | Preparatoria |
|---|---|---|---|
| Metepec | 6 grados | 3 grados | 1er semestre (26B) |
| Tenancingo | 6 grados | 3 grados | — |
| Ixtapan de la Sal | 6 grados | 1° y 2° | — |

## Stack Tecnológico

| Capa | Tecnología |
|---|---|
| Base de datos | PostgreSQL 18 + pgvector 0.8.2 |
| Caché / Cola | Valkey 9.1.0 |
| Almacenamiento | MinIO |
| Autenticación | Authentik 2026.5.2 (OIDC + Google Workspace SSO) |
| Backend API | FastAPI 0.136+ (Python 3.12) |
| Frontend | Angular 22 + PrimeNG |
| Tareas async | Celery 5.6+ |
| BI / Reportes | Apache Superset 6.1.0 |
| Horarios | aSc TimeTables (integración XML) |
| Infraestructura | Docker Compose · Ubuntu 24 ARM (OCI) |
| Agente IA | LangChain + LangGraph + Claude API |

## Estructura del Proyecto

```
ades-nevadi/
├── .agent/                  # Contexto cognitivo del agente residente
│   ├── CONTEXT.md           # Especificación completa del sistema
│   └── STATE.md             # Estado actual del desarrollo
├── db/
│   ├── migrations/          # DDL versionado (Alembic)
│   │   └── 001_initial_schema.sql
│   ├── seeds/               # Datos iniciales ciclo 2026-2027
│   │   ├── 001_datos_base.sql
│   │   ├── 002_grupos_profesores_v3.sql
│   │   ├── 003_alumnos_padres_v2.sql
│   │   ├── 004_plan_estudios.sql
│   │   └── 005_disponibilidad_aulas.sql
│   └── scripts/
│       └── init_multi_db.sh
├── backend/                 # FastAPI (en desarrollo)
├── frontend/                # Angular + PrimeNG (en desarrollo)
├── integrations/
│   ├── asc_horarios/        # Exportador/importador XML para aSc TimeTables
│   ├── superset/            # Configuración y Dockerfile Superset
│   └── cube/                # Capa semántica (Fase 4)
├── infrastructure/
│   └── nginx/               # Configuración reverse proxy + TLS
├── docker-compose.yml       # Stack completo
├── .env.example             # Variables de entorno (copiar a .env)
└── README.md
```

## Requisitos

- Docker 29+ y Docker Compose 5+
- Ubuntu 24 LTS (ARM64 o x86_64)
- 24 GB RAM mínimo recomendado
- Dominio con DNS configurado (para certbot)

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/imarthe75/ades-nevadi.git /opt/ades
cd /opt/ades
```

### 2. Configurar variables de entorno

```bash
cp .env.example .env
nano .env  # llenar contraseñas y configuración
```

### 3. Crear red compartida y certificado SSL

```bash
docker network create aura-network

sudo certbot certonly --standalone -d tu-dominio.com \
  --email admin@correo.com --agree-tos --non-interactive
```

### 4. Levantar infraestructura base

```bash
docker compose up -d postgres valkey minio
```

### 5. Inicializar base de datos

```bash
# Crear bases adicionales y extensiones
docker compose exec postgres psql -U ades_admin -d postgres \
  -c "CREATE USER authentik WITH PASSWORD 'TU_PASS';"
docker compose exec postgres psql -U ades_admin -d postgres \
  -c "CREATE DATABASE authentik OWNER authentik;"
docker compose exec postgres psql -U ades_admin -d postgres \
  -c "CREATE DATABASE superset OWNER ades_admin;"

# Aplicar DDL
docker compose exec -T postgres psql -U ades_admin -d ades \
  < db/migrations/001_initial_schema.sql

# Cargar seeds (ciclo 2026-2027)
for seed in db/seeds/*.sql; do
  docker compose exec -T postgres psql -U ades_admin -d ades < "$seed"
done
```

### 6. Levantar stack completo

```bash
docker compose up -d
```

### 7. Configurar Authentik

Acceder a `https://tu-dominio/auth` y configurar:
- Google Workspace SSO para personal docente/administrativo
- Aplicación OIDC `ades-frontend`
- Aplicación OIDC `superset`

Actualizar `.env` con los `client_secret` generados y reiniciar:

```bash
docker compose restart ades-api superset
```

## Datos del Ciclo 2026-2027

| Entidad | Cantidad |
|---|---|
| Grupos | 54 |
| Profesores | 80 |
| Alumnos | 1,620 |
| Inscripciones | 1,620 |
| Materias en plan | 222 |
| Temas | 280 |

## Módulos

### Fase 1 — Core (activa)
- Estructura académica (escuelas, planteles, niveles, grupos)
- Planes de estudio y calendarios SEP/UAEMEX
- Usuarios y autenticación OIDC

### Fase 2 — Operación académica
- Asistencias, tareas y entregas
- Calificaciones y evaluaciones por periodo
- Comunicados con acuse digital

### Fase 3 — Módulos especializados
- Horarios vía aSc TimeTables (XML)
- Expediente médico
- Reportes de conducta
- Dashboards Superset

### Fase 4 — IA y analytics
- Asistente LangChain para profesores
- Stack analítico (Debezium → ClickHouse → dbt → Cube)
- Detección predictiva de riesgo académico

## Integración aSc TimeTables

El sistema exporta datos base (grupos, materias, profesores, disponibilidad, aulas)
en formato XML para aSc TimeTables, y re-importa el horario generado a la tabla
`ades_horarios`. Ver `integrations/asc_horarios/`.

## Agente Residente

El proyecto usa Claude Code como agente de desarrollo residente.
El contexto completo del sistema está en `.agent/CONTEXT.md`.

Para iniciar una sesión de desarrollo:

```bash
cd /opt/ades
claude
```

## Licencia

Privado — Instituto Nevadi © 2026
