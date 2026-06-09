# ADES — Administración Escolar Instituto Nevadi
# Contexto Cognitivo del Agente Residente
# Última actualización: 2026-06-09

## Propósito del Sistema
Sistema integral de control escolar para el **Instituto Nevadi**, con 3 planteles
y 3 niveles educativos. Cubre gestión académica, formativa, de salud, comunicación
interna, generación de horarios y certificación digital. No incluye pagos/colegiaturas.

---

## Institución

**Razón social:** Instituto Nevadi
**Sitio web:** https://institutonevadi.edu.mx/
**Eslogan:** EL ÚNICO CAMINO PARA SALIR ADELANTE ES LA EDUCACIÓN.
**Color primario:** #C41724

### Planteles y niveles activos

| Plantel | Primaria (SEP) | Secundaria (SEP) | Preparatoria (UAEMEX) |
|---|---|---|---|
| Metepec | 6 grados | 3 grados | Sem 1-4 activos (26B/27A); sem 5-6 is_active=FALSE |
| Tenancingo | 6 grados | 3 grados | Sem 1-2 activos (26B); sem 3-6 is_active=FALSE |
| Ixtapan de la Sal | 6 grados | 3 grados | Sem 1-6 is_active=FALSE (proyectados, sin fecha) |

Grupos por grado: 2 grupos (A y B). Grupos inactivos se activan ciclo a ciclo sin nueva migración.

### Contactos por plantel
- Metepec: Prolongación Heriberto Enríquez 1001 · 7222971441 / 7223253683 · nevadimetepec@institutonevadi.edu.mx
- Tenancingo: Carretera Tenancingo-Tenería S/N · 7141424323 · nevaditenancingo@institutonevadi.edu.mx
- Ixtapan de la Sal: Independencia Pte. 5 · 7211433015 · nevadiixtapan@institutonevadi.edu.mx

---

## Marco Regulatorio

| Nivel | Autoridad | Ciclo activo | Calendario |
|---|---|---|---|
| Primaria | SEP | 2026-2027 | SEP nacional |
| Secundaria | SEP | 2026-2027 | SEP nacional |
| Preparatoria | UAEMEX | 26B (ago 2026) / 27A (ene 2027) | UAEMEX semestral |

Periodos de evaluación:
- Primaria SEP: 3 bimestres
- Secundaria SEP: 6 bimestres
- Preparatoria UAEMEX: 2 parciales + 1 final + extraordinario por semestre

---

## Roles del Sistema (18 roles — migración 008)

Gestión central: ADMIN_GLOBAL (0), ADMIN_PLANTEL (1)
Dirección: DIRECTOR (2), SUBDIRECTOR (2), COORDINADOR_ADMINISTRATIVO (2), COORDINADOR_RH (2), COORDINADOR_AREA (2 — global, por área académica)
Coordinación: COORDINADOR_ACADEMICO (3), TUTOR (3), ORIENTADOR (3), SECRETARIA_ACADEMICA (3)
Operativo: DOCENTE (4), MEDICO_ESCOLAR (4), PREFECTO (4), APOYO_ACADEMICO (4), APOYO_ADMINISTRATIVO (4)
Comunidad: ALUMNO (5), PADRE_FAMILIA (5)

Auth personal (0-4): Authentik → Google Workspace SSO (@institutonevadi.edu.mx)
Auth comunidad (5): Cuentas locales Authentik permanentes

8 áreas académicas en ades_areas_academicas: Matemáticas, Español, Inglés, Ciencias,
Historia y Geografía, Formación Cívica, Educación Física, Tecnología.

---

## Estado de Fases (2026-06-09)

| Fase | Estado | Notas |
|---|---|---|
| FASE 1 — Core | Completa | 30 operaciones REST, modelos SQLAlchemy, seeds |
| FASE 2 — Operación académica | Completa | +24 ops: calificaciones, asistencias, tareas, clases |
| FASE 3 — Especializados | Completa | Horarios+aSc, Expediente Médico, Conducta, Evaluación Docente 360, Boletas PDF WeasyPrint |
| FASE 4 — IA + Analytics | En progreso | Asistente IA Claude, alertas riesgo activos. Pendiente: ClickHouse, Superset BI, Learning Paths |
| FASE 5 — Blockchain | Pendiente | Firmas Ed25519 luego anclaje Polygon |
| Migración 008 | Completa | 4 roles nuevos, ades_areas_academicas, ades_coordinaciones_area |
| Migración 009 | Completa | ades_parametros_sistema 18 params, ades_promociones_pendientes, cerrar_ciclo_y_promover() |
| Frontend Auth | Corregido | app.html limpio, authGuard, oidcRedirectUri a ades.setag.mx/callback |

---

## Módulos del Sistema

### FASE 1 (Completa)
1. Identidad Institucional
2. Catálogo Geográfico SEPOMEX
3. Estructura Académica
4. Planes de Estudio
5. Inscripciones
6. Profesores
7. Calendario Escolar
8. Usuarios y Autenticación OIDC/RBAC

### FASE 2 (Completa)
9. Asistencias
10. Tareas y Entregas (generación automática al cargar plan)
11. Calificaciones por bimestre/parcial con rúbricas
12. Evaluaciones ordinarias, finales, extraordinarios
13. Planeación de Clases
14. Comunicados con acuse digital

### FASE 3 (Completa)
15. Horarios — aSc TimeTables XML import/export
16. Expediente Médico
17. Reportes de Conducta
18. Reportes Académicos — boletas PDF WeasyPrint
19. Evaluación Docente 360°

### FASE 4 (En progreso)
20. Asistente IA — LangChain/LangGraph + Claude API
21. Riesgo Académico — alertas < 70 activas
22. Dashboard BI — Superset (pendiente configuración completa)
23. Learning Paths — rutas de refuerzo adaptativas
24. Grade Analytics — tendencias, análisis de cohorte
25. Learning Analytics — patrones de aprendizaje
26. Gradebook — panel spreadsheet, calificación masiva, rúbrica
27. Mi Progreso — vista alumno con countdown de tareas
28. Ponderación Config — esquemas por nivel/materia, suma=100%

### FASE 5 — Certificación blockchain (Pendiente)

Etapa A — Firmas Ed25519 (costo $0):
- Worker Celery firma SHA-256 del PDF con llave Ed25519 del instituto
- Firma + hash en ades_certificados_digitales
- PDF incluye QR a https://ades.setag.mx/verificar/{ref_uuid}
- Stack: Python cryptography + qrcode + WeasyPrint

Etapa B — Anclaje Polygon PoS (costo ~$5-20 USD/año):
- Hash en smart contract Solidity en Polygon PoS
- blockchain_tx guardado en ades_certificados_digitales
- Stack: web3.py + Remix IDE + Blockcerts MIT

Tabla ades_certificados_digitales incluye: reporte_id, hash_documento, firma_ed25519,
clave_publica_ref, blockchain_red, blockchain_tx, blockchain_bloque, verificable_url.

### Módulos inspirados en Moodle
FASE 2: Quiz Engine, Activity Completion, Content Bank, Notificaciones, Foros
FASE 3: Badges/Gamificación, Competency Framework, Encuestas
FASE 4: Grade Analytics, Learning Analytics, Learning Plans
ADES es SIS no LMS. No se adopta Moodle completo.

---

## Generación Automática de Tareas
Al insertar en ades_materias_plan, Celery genera ades_tareas para todo el ciclo
vinculadas a los temas. Alumnos suben archivos (MinIO/S3) como entrega.

---

## Diseño Frontend — Estilo Oracle APEX con PrimeNG

Patrones APEX implementados con PrimeNG:
- Interactive Report → p-table filterDisplay="row" (todas las listas)
- Editable Report → p-table p-cellEditor (libreta calificaciones)
- Master-Detail → split panel + router (Grupo→Alumnos→Detalle)
- ContextService → selector plantel+ciclo persistente en toolbar
- LOV → p-autoComplete/p-dropdown lazy
- KPI Cards → p-card con ícono + valor grande

Reglas UX:
1. Selector de contexto (plantel + ciclo) siempre visible
2. Fechas DD-MM-YYYY en UI, ISO 8601 en API
3. Español incluyendo mensajes validación PrimeNG
4. Tablas compactas densidad tipo APEX
5. Columna acciones fija a la derecha
6. Guardado bulk en calificaciones/asistencias, no auto-save
7. Responsive tablet-first

Tipografía: Jost (headings, KPIs) + Inter (body, tablas)
Exportación: CSV, XLSX (SheetJS encabezado rojo Nevadi), PDF backend

---

## Reglas de Negocio Críticas

0. Gradebook escalas: SEP 0-10 mínimo 6.0 / UAEMEX 0-100 mínimo 60.
   En ades_niveles_educativos.escala_maxima y minimo_aprobatorio.
   Ponderación suma=100%. Esquema materia > genérico nivel.
   Calificación cerrada inmutable sin ADMIN. Ajuste requiere justificación >= 20 chars.
1. Múltiples docentes de la misma materia por plantel — sin restricción unicidad.
   Titular primaria cubre todas las materias de su grupo.
2. Secundaria/prep: uno o más profesores por materia por plantel por grupo.
3. Grupos: siempre A y B, máximo 2 por grado.
4. Ciclo prep vigente: 26B. Metepec sem 1-4. Tenancingo sem 1-2.
5. Ixtapan: 3 grados secundaria completos. Prep proyectada is_active=FALSE.
6. Tenancingo prep: sem 1-2 activos, sem 3-6 is_active=FALSE.
7. Calendario SEP único para primaria y secundaria. UAEMEX solo para prep.
8. Fechas UI: DD-MM-YYYY. Idioma: español.
9. clave_hash nullable para SSO. oidc_sub almacena sub del JWT Authentik.
10. ades_disponibilidad_docente con restricciones horarias para aSc.
11. Familia: padre puede tener múltiples hijos. ades_contactos_familiares
    vincula persona_id a estudiante_id con banderas es_tutor_legal, puede_recoger,
    es_contacto_emergencia.
12. Cierre y promoción: cerrar_ciclo_y_promover() inscribe automáticamente.
    BAJA no se reinscribe. REPROBADO mismo grado. EGRESADO no se reinscribe.
    Sin grupo destino va a ades_promociones_pendientes.

---

## Administración del Sistema

ades_parametros_sistema (migración 009) — 18 parámetros en 5 grupos:
GENERAL: NOMBRE_SISTEMA, NOMBRE_INSTITUCION, SLOGAN
CONTACTO: TEL_PRINCIPAL, EMAIL_CONTACTO, SITIO_WEB
APARIENCIA: LOGO_URL, COLOR_PRIMARIO (#C41724), COLOR_SECUNDARIO, FAVICON_URL
SEP: CLAVE_CCT_PRIMARIA/SECUNDARIA/PREPARATORIA, ESCALA_CALIFICACION
FUNCIONALIDAD: PORTAL_PADRES_ACTIVO, ENCUESTAS_ACTIVO, IA_ACTIVO, OPENAI_API_KEY

es_publico=TRUE: sin auth. es_publico=FALSE: solo ADMIN_GLOBAL.
tipo_valor: TEXTO, NUMERO, BOOLEAN, URL, COLOR, JSON.

---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Base de datos | PostgreSQL + pgvector | 18.4 + 0.8.2 |
| PK estándar | UUID v7 uuidv7() nativo PG18 | — |
| Caché | Valkey | 9.1.0 |
| Backend | FastAPI + SQLAlchemy + Celery | 0.136 / 2.0.50 / 5.6.3 |
| Frontend | Angular + PrimeNG | 22 |
| Auth IdP | Authentik OIDC/OAuth2 | 2026.5.2 |
| Almacenamiento | MinIO S3 | 2025-09-07 |
| BI | Apache Superset | 6.1.0 |
| Horarios | aSc TimeTables XML | — |
| PDF | WeasyPrint | — |
| Blockchain Fase 5 | web3.py + Polygon + Blockcerts | — |
| Infra | Docker Compose Ubuntu 24 ARM OCI | — |
| Auditoría | Esquema auditoria triggers PL/pgSQL | — |
| Migraciones | Alembic | 1.18.4 |
| IA Agente | LangChain + LangGraph + Claude API | — |
| Servidor | ARM OCI 4 cores 24 GB RAM | — |
| Dominio dev | ades.setag.mx 129.213.35.140 | — |
| SSL | Let's Encrypt válido 2026-09-01 | — |

---

## Convenciones de Base de Datos

- Prefijo: ades_
- PK: id UUID NOT NULL DEFAULT uuidv7()
- Ref: ref UUID NOT NULL UNIQUE DEFAULT uuidv7() para business key SCD2
- Auditoría: trigger auditoria.trg_auditoria_biu en todas las tablas
- Soft delete: is_active BOOLEAN NOT NULL DEFAULT TRUE
- Estatus: ades_estatus + FK estatus_id UUID
- Nombres: snake_case español
- FKs: sufijo _id UUID
- Comentarios obligatorios en tablas y columnas no obvias
- row_version para concurrencia optimista
- Migraciones: db/migrations/ con prefijo 3 dígitos (018_xxx.sql)

---

## Estructura del Repositorio

/opt/ades/
├── .agent/ (CONTEXT.md, STATE.md, MAP.md)
├── DECISIONS/ (ADRs)
├── db/ (migrations 001-018+, seeds, scripts)
├── backend/ (FastAPI: api, models, schemas, services, worker, alembic)
├── frontend/ (Angular 22 + PrimeNG)
├── integrations/ (asc_horarios, superset, cube)
├── infrastructure/nginx/
├── memory/ (SemanticCache, LongTermMemory)
├── agents/ (subagentes del framework)
├── data/ (volúmenes Docker — en .gitignore)
├── docker-compose.yml
└── .env (en .gitignore)

---

## Datos Cargados (Seeds 2026-2027)

Escuelas: 1 | Planteles: 3 | Grupos activos: 54 | Profesores: 80
Asignaciones: 444 | Alumnos: 1,620 | Inscripciones: 1,620
Padres: 1,620 | Usuarios: 1,668 | Materias en plan: 222 | Temas: 280

---

## Decisiones de Arquitectura

| Fecha | Decisión | Motivo |
|---|---|---|
| 2026-06 | PostgreSQL 18 + pgvector | Versión más reciente; pgvector memoria semántica |
| 2026-06 | UUID v7 como PK | Time-ordered, sin fragmentación, nativo PG18 |
| 2026-06 | Valkey en lugar de Redis | Fork opensource activo, 100% compatible |
| 2026-06 | Authentik como IdP | Opensource, Google SSO, usuarios locales |
| 2026-06 | Firmas Ed25519 antes de blockchain | Costo cero, compatible con Polygon |
| 2026-06 | Polygon PoS para blockchain | Gas 1000x más barato, ~$5-20/año |
| 2026-06 | aSc TimeTables para horarios | Problema NP-difícil ya resuelto |
| 2026-06 | Angular + PrimeNG estilo APEX | PrimeNG cubre los mismos patrones APEX |
| 2026-06 | WeasyPrint para PDF | Python nativo, CSS-based, sin Java |
| 2026-06 | Superset 6.1.0 | Última estable; Dockerfile custom con psycopg2 |

---

## Prioridad de Desarrollo

FASE 1 Completa → FASE 2 Completa → aSc Completa → FASE 3 Completa → FASE 4 En progreso → FASE 5 Pendiente

El agente NUNCA debe saltarse fases ni generar código de fases superiores
antes de tener la fase anterior funcionando y probada.
