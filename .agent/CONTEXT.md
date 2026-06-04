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

| Plantel      | Primaria (SEP) | Secundaria (SEP) | Preparatoria (UAEMEX)                          |
|--------------|:--------------:|:----------------:|:----------------------------------------------:|
| Metepec      | 6 grados       | 3 grados         | Sem 1-6 (activos 1-4 ciclo 26B/27A)            |
| Tenancingo   | 6 grados       | 3 grados         | Sem 1-6 (activos 1-2 ciclo 26B)                |
| Ixtapan      | 6 grados       | 3 grados         | Sem 1-6 (todos `is_active=FALSE` — proyectados) |

**Grupos por grado:** 2 grupos (A y B) en todos los niveles y planteles.
**Grupos inactivos (futuros):** Metepec prep sem 5-6, Tenancingo prep sem 3-6 e Ixtapan prep sem 1-6 tienen `is_active = FALSE` — se activan ciclo a ciclo sin necesidad de nueva migración.

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

## Roles del Sistema (18 roles — migración 008)

### Gestión central
| Rol           | Nivel | Alcance                       |
|---------------|:-----:|-------------------------------|
| ADMIN_GLOBAL  |   0   | Todos los planteles y niveles |
| ADMIN_PLANTEL |   1   | Un plantel completo           |

### Dirección y coordinación
| Rol                       | Nivel | Alcance                                                              |
|---------------------------|:-----:|----------------------------------------------------------------------|
| DIRECTOR                  |   2   | **Por nivel educativo dentro del plantel** — hasta 3 directores por plantel (Primaria / Secundaria / Preparatoria) |
| SUBDIRECTOR               |   2   | Suplente del director, mismo alcance                                 |
| COORDINADOR_ADMINISTRATIVO|   2   | Procesos administrativos, logística, relación padres — por plantel/nivel |
| COORDINADOR_RH            |   2   | Personal docente y administrativo, contratos                         |
| COORDINADOR_AREA          |   2   | **Global** — coordina un área académica (Matemáticas, Español, Inglés, Ciencias…) a través de todos los planteles |

### Coordinación académica y orientación
| Rol                   | Nivel | Alcance                                                            |
|-----------------------|:-----:|--------------------------------------------------------------------|
| COORDINADOR_ACADEMICO |   3   | Coordinación académica por nivel dentro del plantel                |
| TUTOR                 |   3   | Seguimiento académico personalizado de un grupo de estudiantes     |
| ORIENTADOR            |   3   | Orientación educativa y vocacional (secundaria / preparatoria)     |
| SECRETARIA_ACADEMICA  |   3   | Expedientes, certificados, actas, inscripciones                    |

### Personal operativo
| Rol                  | Nivel | Alcance                                                |
|----------------------|:-----:|--------------------------------------------------------|
| DOCENTE              |   4   | Sus grupos y materias asignadas                        |
| MEDICO_ESCOLAR       |   4   | Expedientes médicos de su plantel                      |
| PREFECTO             |   4   | Disciplina, supervisión de pasillos y accesos          |
| APOYO_ACADEMICO      |   4   | Recursos, biblioteca, laboratorio                      |
| APOYO_ADMINISTRATIVO |   4   | Trámites, archivo, atención                            |

### Comunidad educativa
| Rol           | Nivel | Alcance                         |
|---------------|:-----:|---------------------------------|
| ALUMNO        |   5   | Su propio expediente y materias |
| PADRE_FAMILIA |   5   | Expedientes de sus hijos        |

**Auth personal (niveles 0-4):** Authentik local → Google Workspace SSO cuando esté disponible.
**Auth comunidad (nivel 5):** Cuentas locales Authentik permanentes.

**Autenticación:** Authentik como IdP. Personal docente/administrativo usa Google Workspace SSO
con cuentas @institutonevadi.edu.mx. Alumnos y padres usan cuentas locales en Authentik.

### Estructura de personal real por plantel
- **Hasta 3 directores** por plantel — uno por nivel educativo activo
- **Un COORDINADOR_ACADEMICO y un COORDINADOR_ADMINISTRATIVO por nivel educativo** activo
- **Tutores**: uno o más por plantel, asignados a grupos específicos
- **ORIENTADORES**: en secundaria y preparatoria
- **APOYO_ADMINISTRATIVO / APOYO_ACADEMICO**: según tamaño del plantel

### Coordinación académica global
Coordinadores de área (COORDINADOR_AREA) gestionados en `ades_coordinaciones_area` → `ades_areas_academicas` (8 áreas: Matemáticas, Español, Inglés, Ciencias, Historia y Geografía, Formación Cívica, Educación Física, Tecnología).

### Regla eliminada — inglés
~~Un profesor de inglés por plantel cubre todos los grupos de primaria~~
Un plantel puede tener **múltiples docentes de la misma materia**. No hay restricción de unicidad por asignatura.

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
23. **Learning Paths** — rutas de refuerzo adaptativas para alumnos en riesgo (inspirado en Moodle)
24. **Grade Analytics** — tendencias, alertas <70, análisis de cohorte (inspirado en Moodle)
25. **Learning Analytics** — acceso a contenidos, tiempo dedicado, patrones de aprendizaje

### Módulos FASE 10 — Gradebook Curricular Integrado

26. **Gradebook** — Panel tipo spreadsheet para profesores: actividades × alumnos,
    calificación masiva, drawer de calificación con rúbrica.
27. **Mi Progreso** — Vista del alumno: cards por materia con % progreso, tareas pendientes
    con countdown, historial de entregas calificadas, subida de archivos.
28. **Ponderación Config** — Admin de esquemas de ponderación: Examen/Tarea/Proyecto/
    Asistencia/Comportamiento por nivel o materia, validación suma=100%, historial de versiones.

**Cálculo automático de calificación final:**
- Función PostgreSQL `calcular_calificacion_periodo()` — idempotente, disparada por 3 triggers
- Hereda esquema del nivel si no hay específico para la materia
- Escala dinámica: SEP 0–10 (1 decimal) | UAEMEX 0–100 (entero)
- `score_por_item` en JSONB para desglose auditable por ítem
- Calificación cerrada = inmodificable sin rol ADMIN
- Ajuste manual requiere justificación ≥ 20 caracteres (auditoría)
- Reporte de cobertura curricular: temas vs. actividades registradas

### Módulos inspirados en Moodle (análisis 2026-06)

De análisis comparativo con github.com/moodle/moodle se incorporan:
- **FASE 2:** Quiz Engine, Activity Completion, Content Bank, Notifications, Foros
- **FASE 3:** Badges/Gamificación, Competency Framework, Encuestas, Certificados Digitales
- **FASE 4:** Grade Analytics, Learning Analytics, Learning Plans

No se adopta Moodle completo: ADES es SIS (School Information System), no LMS.
Moodle es single-tenant PHP/MySQL; ADES es multi-plantel FastAPI/PG18/Angular con regulaciones SEP/UAEMEX.

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

## Diseño Frontend — Referencia Oracle APEX

El frontend Angular se diseña siguiendo los patrones de UX de **Oracle APEX** (estilo empresarial,
denso en información, orientado a productividad) usando **PrimeNG** como librería de componentes.

### Patrones APEX → PrimeNG

| Patrón APEX | Componente PrimeNG | Uso en ADES |
|---|---|---|
| **Interactive Report** | `p-table` + `filterDisplay="row"` + `sortField` + `exportCSV()` | Todas las listas: alumnos, grupos, calificaciones |
| **Editable Interactive Report** | `p-table` + `p-cellEditor` | Libreta de calificaciones — edición inline por celda |
| **Faceted Search** | `p-multiSelect` + sidebar | Filtros de búsqueda de alumnos/profesores |
| **Master-Detail** | Split panel o router con panel derecho | Grupo → Alumnos → Detalle alumno |
| **Classic Report** | `p-table` modo lectura + Print CSS | Boletas imprimibles |
| **Page Items globales (Application Items)** | `ContextService` + `p-dropdown` en toolbar | Selector de plantel + ciclo que persiste en toda la app |
| **LOV (List of Values)** | `p-autoComplete` / `p-dropdown` con lazy API | Selector de grupos, profesores, materias |
| **Calendar Region** | `FullCalendar` / `p-fullCalendar` | Vista de asistencias y calendario escolar |
| **Pase de lista rápido** | Toggle 1-click PRESENTE/AUSENTE/TARDE | Basado en APEX List + checkbox inline |
| **Breadcrumb** | `p-breadcrumb` en header de página | Inicio > Plantel > Grupo > Materia |
| **Row Actions** | `p-splitButton` en columna de acciones | Editar, dar de baja, ver detalle |
| **Chart Region** | `p-chart` (Chart.js) | KPIs: promedio del grupo, % asistencia |
| **KPI Cards** | `p-card` con ícono + valor grande | Dashboard principal por rol |
| **Notifications** | `p-toast` + `p-messages` | Guardar, errores de validación, alertas |

### Reglas de UX globales

1. **Selector de contexto** (plantel + ciclo) siempre visible en la barra superior — todo el contenido
   de la app filtra automáticamente por ese contexto. Equivale a los Application Items de APEX.
2. **Fechas:** siempre DD-MM-YYYY en pantalla; ISO 8601 en la API.
3. **Idioma:** español, incluyendo mensajes de validación de PrimeNG.
4. **Densidad de información:** tablas compactas (sin padding excesivo), similar a APEX.
5. **Acciones en tabla:** columna fija a la derecha con íconos (editar/ver/baja) — nunca floating.
6. **Guardado bulk:** en libreta de calificaciones y pase de lista, acumular cambios localmente
   y enviar con un solo botón "Guardar" — no auto-save por célula (evita sobrecarga de la API).
7. **Responsive:** diseño tablet-first (los docentes usan tablets en el salón).

---

## Reglas de Negocio Críticas

0. **Gradebook — escalas:** Primaria/Secundaria SEP: 0–10 (mínimo aprobatorio 6.0). Preparatoria UAEMEX: 0–100 (mínimo 60). Almacenado en `ades_niveles_educativos.escala_maxima` y `minimo_aprobatorio`.
   **Gradebook — ponderación:** suma siempre = 100%. Esquema específico de materia tiene prioridad sobre genérico del nivel.
   **Gradebook — ajuste:** calificación cerrada es inmutable sin ADMIN. Ajuste manual exige justificación ≥ 20 chars.

1. **Profesor en primaria:** un titular por grupo para todas las materias. **Puede haber más de un docente de la misma materia (incluyendo inglés) por plantel.** No existe restricción de unicidad por asignatura.
2. **Profesor en secundaria/preparatoria:** uno o más profesores por materia por plantel, asignados individualmente a cada grupo.
3. **Grupos:** siempre A y B. Nunca más de 2 por grado salvo instrucción explícita.
4. **Ciclo preparatoria:** ciclo vigente 26B (agosto 2026). Metepec sem 1-4 activos; Tenancingo sem 1-2 activos.
5. **Ixtapan secundaria:** 3 grados completos (1°, 2°, 3°). **Preparatoria proyectada:** los 6 semestres UAEMEX existen en la base de datos con `is_active=FALSE`. Sin fecha confirmada de activación; se activarán semestre a semestre sin nueva migración.
6. **Tenancingo preparatoria:** incorporada. Semestres 1-2 activos; sem 3-6 `is_active=FALSE` (futuros).
7. **Calendario:** SEP aplica a primaria y secundaria de todos los planteles (es único).
   UAEMEX aplica solo a preparatoria Metepec.
8. **Formato de fechas en UI:** DD-MM-YYYY. Idioma: español.
9. **OIDC:** el campo `clave_hash` en usuarios es nullable para personal con SSO.
   Campo adicional `oidc_sub` almacena el `sub` del JWT de Authentik.
10. **Horarios aSc:** `ades_aulas` tiene capacidad y tipo (aula, laboratorio, sala cómputo).
    `ades_disponibilidad_docente` almacena restricciones horarias por profesor para aSc.
11. **Familia — un padre puede tener múltiples hijos.** `ades_contactos_familiares` vincula `persona_id → estudiante_id`; la misma persona puede ser contacto de varios alumnos. Un alumno puede tener múltiples contactos (padre, madre, tutor, abuelo, etc.) con banderas `es_tutor_legal`, `puede_recoger`, `es_contacto_emergencia`.
12. **Ciclo escolar — cierre y promoción automática:** función PG `cerrar_ciclo_y_promover(ciclo_origen_id, ciclo_destino_id)` inscribe automáticamente a alumnos activos en el siguiente ciclo. Casos especiales: `BAJA` no se reinscribe, `REPROBADO` queda en el mismo grado, `EGRESADO` (último grado del nivel) no se reinscribe. Alumnos sin grupo destino quedan en `ades_promociones_pendientes` para asignación manual.

---

## Administración del Sistema

El rol `ADMIN_GLOBAL` (nivel 0) es el administrador del sistema. Accede a todas las funcionalidades.

### Parámetros configurables — `ades_parametros_sistema`

Tabla key-value con 18 parámetros iniciales organizados en 5 grupos:

| Grupo | Claves |
|-------|--------|
| `GENERAL` | `NOMBRE_SISTEMA`, `NOMBRE_INSTITUCION`, `SLOGAN` |
| `CONTACTO` | `TEL_PRINCIPAL`, `EMAIL_CONTACTO`, `SITIO_WEB` |
| `APARIENCIA` | `LOGO_URL`, `COLOR_PRIMARIO` (#C41724), `COLOR_SECUNDARIO`, `FAVICON_URL` |
| `SEP` | `CLAVE_CCT_PRIMARIA/SECUNDARIA/PREPARATORIA`, `ESCALA_CALIFICACION` |
| `FUNCIONALIDAD` | `PORTAL_PADRES_ACTIVO`, `ENCUESTAS_ACTIVO`, `IA_ACTIVO`, `OPENAI_API_KEY` |

- `es_publico = TRUE` → API expone el valor sin autenticación (nombre institución, colores)
- `es_publico = FALSE` → solo ADMIN_GLOBAL puede leer/escribir (CCTs, API keys)
- `tipo_valor`: TEXTO, NUMERO, BOOLEAN, URL, COLOR, JSON
- El logo se sube a MinIO → `LOGO_URL` recibe la URL pública

### Módulo admin — funcionalidades esperadas (pendiente de implementar)

- Gestión de usuarios y cuentas de login (delegada a Authentik — OIDC)
- Gestión de roles y permisos
- Gestión de materias y plan de estudios
- Parámetros del sistema (UI sobre `ades_parametros_sistema`)
- Identidad visual (logo, colores — sobre `ades_identidad_institucional`)
- Ciclos escolares: crear nuevo ciclo + ejecutar `cerrar_ciclo_y_promover()`
- Revisión de `ades_promociones_pendientes` y asignación manual

---

## Stack Tecnológico

| Capa               | Tecnología                                      |
|--------------------|-------------------------------------------------|
| Base de datos       | PostgreSQL 18 + pgvector · PK: UUID v7 (`uuidv7()`)|
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
- **PK:** `id UUID NOT NULL DEFAULT uuidv7()` — time-ordered, nativo en PG18, sin fragmentación de índice
- **Ref:** `ref UUID NOT NULL UNIQUE DEFAULT uuidv7()` — business key para sistemas externos (SCD2)
- **Auditoría:** todas las tablas usan el trigger `auditoria.trg_auditoria_biu` del framework
- **Soft delete:** `is_active BOOLEAN NOT NULL DEFAULT TRUE`
- **Estatus por entidad:** tabla `ades_estatus` + FK `estatus_id UUID`; valores típicos: ACTIVO, INACTIVO, BAJA, EGRESADO, SUSPENDIDO
- **Nombres:** snake_case, en español, descriptivos
- **FKs:** sufijo `_id` de tipo `UUID` (ej. `plantel_id UUID`, `ciclo_escolar_id UUID`)
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

## Estado de Fases (2026-06-04)

| Fase | Estado | Notas |
|------|--------|-------|
| FASE 1 — Core | ✅ Completa | 30 operaciones REST, modelos SQLAlchemy, seeds |
| FASE 2 — Operación académica | ✅ Completa | +24 ops: calificaciones, asistencias, tareas, clases |
| FASE 3 — Especializados | ✅ Completa | Horarios+aSc, Expediente Médico, Conducta, Evaluación Docente 360°, Boletas PDF (WeasyPrint) |
| FASE 4 — IA + Analytics | 🔄 En progreso | Asistente IA (Claude), alertas de riesgo académico activos. Pendiente: ClickHouse, Superset BI, Learning Paths |
| Migración 008 | ✅ Completa | 4 nuevos roles, tablas `ades_areas_academicas` + `ades_coordinaciones_area`, 8 áreas |
| Migración 009 | ✅ Completa | `ades_parametros_sistema` (18 params), `ades_promociones_pendientes`, función `cerrar_ciclo_y_promover()` |
| Frontend Auth | ✅ Corregido | `app.html` limpio, `authGuard` creado, `oidcRedirectUri` → ades.setag.mx/callback |

## Exportación de tablas (patrón APEX)

Todas las tablas del sistema deben ofrecer exportación al estilo Oracle APEX:
- **CSV**: `ExportService.toCSV()` — sin dependencias
- **XLSX**: `ExportService.toXLSX()` — SheetJS con encabezado rojo Nevadi
- **PDF**: Descarga desde backend (`/boletas`, `/ai/alertas`, etc.) para documentos oficiales

El `ExportService` ya está disponible como `providedIn: 'root'`. Inyectar y añadir botones CSV + Excel en el `page-header` de cada componente con tabla.

## Tipografía Institucional

- **Jost** — headings, KPIs, títulos de página, marca en topbar, valores numéricos grandes
- **Inter** — body text, tablas, labels, formularios, todo el contenido denso de datos
- Fuentes cargadas desde Google Fonts en `index.html` con `display=swap`

## Prioridad de Desarrollo

FASE 1 ✅ → FASE 2 ✅ → integración aSc ✅ → FASE 3 🔄 → FASE 4 ⏳

El agente NUNCA debe saltarse fases ni generar código de fases superiores
antes de tener la fase anterior funcionando y probada.
