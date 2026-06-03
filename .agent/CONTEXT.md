# ADES — Administración Escolar Instituto Nevadi
# Contexto Cognitivo del Agente Residente

## Propósito del Sistema
Sistema integral de control escolar para el **Instituto Nevadi**, con 3 planteles
y 3 niveles educativos. El sistema cubre gestión académica, formativa, de salud,
comunicación interna y generación de horarios. No incluye módulo de pagos/colegiaturas.

---

## Institución

**Razón social:** Instituto Nevadi
**Sitio web:** https://institutonevadi.edu.mx/
**Eslogan:** EL ÚNICO CAMINO PARA SALIR ADELANTE ES LA EDUCACIÓN.

### Planteles y niveles activos

| Plantel      | Primaria (SEP) | Secundaria (SEP) | Preparatoria (UAEMEX) |
|--------------|:--------------:|:----------------:|:---------------------:|
| Metepec      | 6 grados       | 3 grados         | 1er semestre (25B)    |
| Tenancingo   | 6 grados       | 3 grados         | —                     |
| Ixtapan      | 6 grados       | 1° y 2° grado    | —                     |

**Grupos por grado:** 2 grupos (A y B) en todos los niveles y planteles.

### Contactos por plantel
- **Metepec:** Prolongación Heriberto Enríquez 1001 · Tel: 7222971441 / 7223253683 · nevadimetepec@institutonevadi.edu.mx
- **Tenancingo:** Carretera Tenancingo-Tenería S/N · Tel: 7141424323 · nevaditenancingo@institutonevadi.edu.mx
- **Ixtapan de la Sal:** Independencia Pte. 5 · Tel: 7211433015 · nevadiixtapan@institutonevadi.edu.mx

---

## Marco Regulatorio

| Nivel       | Autoridad | Ciclo escolar           | Calendario       |
|-------------|-----------|-------------------------|------------------|
| Primaria    | SEP       | 2025-2026               | SEP nacional     |
| Secundaria  | SEP       | 2025-2026               | SEP nacional     |
| Preparatoria| UAEMEX    | 25B (ago) / 26A (ene)   | UAEMEX semestral |

### Periodos de evaluación por nivel
- **Primaria SEP:** 3 bimestres
- **Secundaria SEP:** 6 bimestres
- **Preparatoria UAEMEX:** 2 parciales + 1 final + examen extraordinario por semestre

---

## Roles del Sistema

| Rol                    | Alcance                              | Fuente de identidad        |
|------------------------|--------------------------------------|----------------------------|
| ADMIN_GLOBAL           | Todos los planteles y niveles        | Gmail institucional (OIDC) |
| ADMIN_PLANTEL          | Un plantel completo                  | Gmail institucional (OIDC) |
| COORDINADOR_ACADEMICO  | Un nivel dentro de un plantel        | Gmail institucional (OIDC) |
| DIRECTOR               | Un plantel                           | Gmail institucional (OIDC) |
| DOCENTE                | Sus grupos y materias asignadas      | Gmail institucional (OIDC) |
| MEDICO_ESCOLAR         | Expedientes médicos de su plantel    | Gmail institucional (OIDC) |
| ALUMNO                 | Su propio expediente y materias      | Cuenta local (OIDC)        |
| PADRE_FAMILIA          | Expedientes de sus hijos             | Cuenta local (OIDC)        |

**Autenticación:** Authentik como IdP. Personal docente/administrativo usa Google Workspace SSO
con cuentas @institutonevadi.edu.mx. Alumnos y padres usan cuentas locales en Authentik.

---

## Módulos del Sistema

### Módulos FASE 1 — Core (MVP)
1. **Identidad Institucional** — logo, slogan, colores por plantel/nivel, histórico
2. **Catálogo Geográfico** — SEPOMEX completo (países→estados→municipios→localidades→CP)
3. **Estructura Académica** — escuelas, planteles, niveles, grados, grupos, ciclos escolares
4. **Planes de Estudio** — materias por nivel/grado, temas, carga horaria semanal
5. **Inscripciones** — alumnos por grupo/grado/plantel/ciclo
6. **Profesores** — asignación profesor↔materia↔grupo (primaria: titular de grupo excepto inglés)
7. **Calendario Escolar** — SEP 2025-26 y UAEMEX 25B/26A, días festivos, vacaciones
8. **Usuarios y Autenticación** — roles RBAC, OIDC/JWT, sin contraseña local para personal

### Módulos FASE 2 — Operación académica
9. **Asistencias** — por clase, por alumno, reportes de ausentismo
10. **Tareas y Entregas** — generación automática al cargar plan de estudios; alumnos suben archivos
11. **Calificaciones** — por bimestre/parcial según nivel; rúbricas configurables
12. **Evaluaciones** — exámenes ordinarios, finales, extraordinarios por nivel
13. **Planeación de Clases** — profesor registra avance vs. plan de estudios
14. **Comunicados y Circulares** — con acuse digital de recibo por padre/alumno

### Módulos FASE 3 — Módulos especializados
15. **Horarios** — generación via aSc TimeTables (XML import/export); visualización por rol
16. **Expediente Médico** — alergias, medicamentos autorizados, incidentes, consultas
17. **Reportes de Conducta** — incidentes, seguimiento, compromisos de mejora
18. **Reportes Académicos** — boletas, historial, indicadores por grupo/plantel
19. **Evaluación Docente** — indicadores de desempeño, observación de clases

### Módulos FASE 4 — IA y analítica
20. **Asistente IA** — LangChain/LangGraph, sugerencias académicas, generación de rúbricas
21. **Riesgo Académico** — detección predictiva de alumnos con tendencia a reprobar
22. **Dashboard BI** — Apache Superset, KPIs de asistencia/calificaciones/cobertura curricular

### Integración externa (no construir desde cero)
- **aSc TimeTables** — motor de generación de horarios via XML; ADES exporta datos base,
  importa resultado. Tablas requeridas: `ades_aulas`, `ades_horarios`, `ades_disponibilidad_docente`
- **Google Workspace** — SSO para personal, Gmail relay para notificaciones

---

## Generación Automática de Tareas

Cuando se carga un plan de estudios (`ades_materias_plan`), un proceso Celery
genera automáticamente los registros en `ades_tareas` para todo el ciclo escolar,
vinculados a los temas del plan. Los alumnos ven estos espacios desde el primer día
y pueden subir archivos (MinIO/S3) como entrega.

---

## Reglas de Negocio Críticas

1. **Profesor en primaria:** un titular por grupo para todas las materias EXCEPTO inglés.
   Un profesor de inglés por plantel cubre los 12 grupos de primaria (6 grados × 2 grupos).
2. **Profesor en secundaria/preparatoria:** un profesor por materia por grupo.
3. **Grupos:** siempre A y B. Nunca más de 2 por grado salvo instrucción explícita.
4. **Ciclo preparatoria Metepec:** 25B = agosto 2025. Solo existe 1er semestre activo.
5. **Ixtapan secundaria:** solo 1° y 2° grado (no hay 3°).
6. **Tenancingo:** no tiene preparatoria.
7. **Calendario:** SEP aplica a primaria y secundaria de todos los planteles (es único).
   UAEMEX aplica solo a preparatoria Metepec.
8. **Formato de fechas en UI:** DD-MM-YYYY. Idioma: español.
9. **OIDC:** el campo `clave_hash` en usuarios es nullable para personal con SSO.
   Campo adicional `oidc_sub` almacena el `sub` del JWT de Authentik.
10. **Horarios aSc:** `ades_aulas` tiene capacidad y tipo (aula, laboratorio, sala cómputo).
    `ades_disponibilidad_docente` almacena restricciones horarias por profesor para aSc.

---

## Stack Tecnológico

| Capa               | Tecnología                                      |
|--------------------|-------------------------------------------------|
| Base de datos       | PostgreSQL 18 + pgvector (pgvector/pgvector:pg18)|
| Caché / sesiones   | Valkey (reemplaza Redis)                         |
| Backend API        | FastAPI (Python 3.12)                            |
| Frontend           | Angular + PrimeNG                                |
| Tareas asíncronas  | Celery + Valkey broker                           |
| Autenticación IdP  | Authentik (OIDC/OAuth2)                          |
| Almacenamiento     | MinIO (compatible S3) para archivos de tareas    |
| BI / Reportes      | Apache Superset                                  |
| Horarios           | aSc TimeTables (integración XML)                 |
| Infraestructura    | Docker Compose · Ubuntu 24 ARM (OCI Always Free) |
| Auditoría          | Esquema `auditoria` con triggers PL/pgSQL        |
| Migraciones DDL    | Alembic                                          |
| IA / Agente        | LangChain + LangGraph + Claude API               |

---

## Convenciones de Base de Datos

- **Prefijo de tablas:** `ades_`
- **PK:** `id BIGINT GENERATED ALWAYS AS IDENTITY`
- **UUID estable:** `ref UUID NOT NULL DEFAULT gen_random_uuid()` (UUIDv7 si disponible)
- **Auditoría:** todas las tablas usan el trigger `auditoria.trg_auditoria_biu` del framework
- **Soft delete:** `is_active BOOLEAN NOT NULL DEFAULT TRUE`
- **Estatus por entidad:** tabla `ades_estatus` + FK `estatus_id`; valores típicos: ACTIVO, INACTIVO, BAJA, EGRESADO, SUSPENDIDO
- **Nombres:** snake_case, en español, descriptivos
- **FKs:** sufijo `_id` (ej. `plantel_id`, `ciclo_escolar_id`)
- **Comentarios:** obligatorios en tablas y columnas no obvias
- **row_version:** control de concurrencia optimista en todas las tablas

---

## Estructura del Repositorio

```
/opt/ades/
├── .agent/               # Contexto cognitivo del agente (este archivo aquí)
├── DECISIONS/            # Decisiones de arquitectura registradas
├── db/
│   ├── migrations/       # Alembic — DDL versionado
│   ├── seeds/            # Datos iniciales (Instituto Nevadi, calendarios, etc.)
│   └── scripts/          # Utilidades DBA (análisis, mantenimiento)
├── backend/              # FastAPI
│   ├── app/
│   │   ├── api/          # Routers por módulo
│   │   ├── models/       # SQLAlchemy ORM
│   │   ├── schemas/      # Pydantic schemas
│   │   ├── services/     # Lógica de negocio
│   │   └── worker/       # Celery tasks
│   └── alembic/
├── frontend/             # Angular + PrimeNG
├── integrations/
│   └── asc_horarios/     # Exportador/importador XML para aSc TimeTables
├── memory/               # Del framework base (SemanticCache, LongTermMemory)
├── agents/               # Subagentes especializados del framework
├── docker-compose.yml    # Infraestructura completa
└── docker-compose.dev.yml # Overrides de desarrollo
```

---

## Estado Inicial de Datos (Seeds)

Al ejecutar los seeds deben existir:
- Instituto Nevadi con sus 3 planteles y datos de contacto
- Niveles: PRIMARIA, SECUNDARIA, PREPARATORIA
- Grados según plantel (ver tabla arriba)
- 2 grupos (A/B) por grado activo
- Ciclos escolares: "2025-2026" (SEP), "25B" y "26A" (UAEMEX)
- Calendario SEP 2025-2026 con días festivos y vacaciones oficiales
- Calendario UAEMEX 25B con períodos de parciales y finales
- Roles del sistema (8 roles definidos arriba)
- Estatus por entidad
- Profesores ficticios según regla de negocio #1 y #2
- 30 alumnos ficticios por grupo con un padre de familia cada uno
- Materias por nivel según plan SEP/UAEMEX

## Prioridad de Desarrollo

FASE 1 → FASE 2 → integración aSc → FASE 3 → FASE 4

El agente NUNCA debe saltarse fases ni generar código de fases superiores
antes de tener la fase anterior funcionando y probada.
