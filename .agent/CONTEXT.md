# ADES — Administración Escolar Instituto Nevadi
# Contexto Cognitivo del Agente Residente
# Última actualización: 2026-06-23

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
- Primaria SEP: 3 bimestres (NEM 1°-2° → evaluación cualitativa A/B/C/D)
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

## Estado de Fases (2026-06-23)

| Fase | Estado | Notas |
|---|---|---|
| FASE 1 — Core | ✅ Completa | 30 ops REST, modelos SQLAlchemy, seeds |
| FASE 2 — Operación académica | ✅ Completa | +24 ops: calificaciones, asistencias, tareas, clases |
| FASE 3 — Especializados | ✅ Completa | Horarios+aSc, Expediente Médico, Conducta, Eval. Docente 360°, Boletas PDF WeasyPrint |
| FASE 4 — IA + Analytics | ✅ Completa | Asistente IA, alertas riesgo, learning paths, grade analytics, gradebook, MI Progreso |
| FASE 5A — Firma Digital Ed25519 | ✅ Completa | firma_digital.py, certificados.py, CertificadosComponent, VerificarComponent (Mig 026) |
| FASE 5B — Blockchain Polygon PoS | ⏳ Diferida | web3.py + contrato Solidity (pendiente despliegue en red PoS) |
| FASE 6 — Evaluaciones+Planeación | ✅ Completa | evaluaciones.py, planeacion.py, rubricas.py, certificados |
| FASE 7 — Encuestas | ✅ Completa | EncuestasComponent con 4 tipos de pregunta |
| FASE 8 — Badges/Gamificación | ✅ Completa | BadgesComponent, auto-evaluación por ciclo |
| FASE 9 — Portal Familias | ✅ Completa | PortalComponent, vista 360° alumno |
| FASE 10 — Gradebook | ✅ Completa | calcular_calificacion_periodo(), triggers, esquemas ponderación |
| FASE 20 — ntfy | ✅ Completa | notificaciones push, canal ades-alertas |
| FASE 21 — Stirling PDF | ✅ Completa | fusión/compresión/firma PDF |
| FASE 22 — Grafana+Prometheus | ✅ Completa | 5 dashboards, 13 alertas, JVM BFF, PgBouncer |
| FASE 23 — n8n | ✅ Completa | automatizaciones, webhooks |
| FASE 24 — Paperless-ngx OCR | ⚠️ Parcial | Contenedor operativo; integración OCR en expediente.py activa, sin UI completa |
| FASE 24P — Padres/RRHH avanzado | ✅ Completa | Gestión padres APEX-style, optimistic locking (Mig 017) |
| FASE 25 — H5P | ✅ Completa | Nodo.js :8091, 10 tipos, iframe player, xAPI (Mig 081) |
| FASE 26 — BigBlueButton | ✅ Completa | API-only checksum SHA-1, reuniones/grabaciones (Mig 082); requiere servidor BBB |
| FASE 27 — Certificación Digital | ✅ Completa | Ed25519, QR PNG, verificación pública /verificar/:folio |
| FASE 28 — Blockchain | ⏳ Diferida | Anclaje Polygon PoS (requiere credenciales y contrato desplegado) |
| FASE 29 — RRHH + MFA | ✅ Completa | Licencias, Capacitaciones, MFA Authentik (Mig 040) |
| FASE 30 — RRHH Avanzado | ✅ Completa | Disponibilidad, Expediente, Asistencia Personal (Mig 041) |
| FASE 31 — Operatividad Avanzada | ✅ Completa | Condiciones Crónicas, Justificaciones, Horarios conflictos (Mig 042) |
| **Auditoría 360°** | ✅ Completa | 64 hallazgos; Sprint 1+2 completos; 344 triggers audit_biu activos |
| **Sprint 5 — Infra & Perf** | ✅ Completa | PgBouncer, postgres_exporter, particionamiento, 4 dashboards Grafana |
| **Sprint 6 — Observability** | ✅ Completa | Micrometer BFF, Celery OCR, chat history IA persistente |
| **Seguridad (IDOR+HTTPS+Rate)** | ✅ Completa | 5 vulnerabilidades corregidas, python-magic MIME, rate limiting slowapi |
| **QA E2E (Suites 01-17)** | ✅ 74.8% | 255/341 passed; P1 ADV-02/03 → CORREGIDO (ValidationUtils 2026-06-23) |
| **Hexagonal Spring BFF** | ✅ ~50/62 | 76 controllers, 50 ApplicationServices, 62 módulos; ADR-0008 |
| **NEM Fase 3 Cualitativa** | ✅ Completa | Escalas A/B/C/D para 1°-2° primaria, config por plantel (Mig 089) |
| **Reporte 911 SEP** | ✅ Completa | Sección IX discapacidad incluida (Spring BFF) |
| **Boleta UAEMEX PDF** | ✅ Completa | /boletas/uaemex/{id} FastAPI→BFF proxy; template weasyprint |
| **Director Dashboard** | ✅ Completa | KPIs generales, gráficas PrimeNG, roleGuard(2) |
| **Classroom Gaps** | ✅ Completa | Detección plagio, feedback multimedia, adecuaciones NEE, Mig 093 |

## ADRs Vigentes

| ADR | Título | Estado |
|-----|--------|--------|
| 0001 | Arquitectura de Génesis | Aceptado |
| 0002 | UUID como Primary Keys | Aceptado |
| 0003 | APEX Component Library | Aceptado |
| 0004 | Firma Digital Ed25519 | Aceptado |
| 0005 | Audit Trail via request.state | Aceptado |
| 0006 | RBAC con Scope de Plantel | Aceptado |
| 0007 | JWKS Async TTL Cache | Aceptado |
| 0008 | Hexagonal/SOLID Migration BFF | En progreso (~80%) |
| 0009 | Seguridad IDOR+HTTPS+Rate Limiting | Aceptado |
| 0009b | Vault+PgBouncer Secrets | Aceptado |
| 0010 | Hexagonal Módulos Planos Restantes | Aceptado |
| 0011 | Boleta NEM FastAPI+Jinja | Aceptado |

---

## Módulos del Sistema (Frontend — 59 componentes)

### FASE 1 (Completa)
1. Identidad Institucional — planteles, institución
2. Catálogo Geográfico SEPOMEX — selector-geo, colonias
3. Estructura Académica — grupos, grados, niveles
4. Planes de Estudio — materias, temarios NEM/CBU
5. Inscripciones — admisión, reinscripción, movilidad
6. Profesores — CRUD, asignaciones, disponibilidad
7. Calendario Escolar — ciclos SEP/UAEMEX
8. Usuarios y Autenticación OIDC/RBAC — admin, roles

### FASE 2 (Completa)
9. Asistencias — pase de lista, estados (PRESENTE/AUSENTE/TARDE/JUSTIFICADA)
10. Tareas y Entregas — CRUD + MinIO + calificación
11. Calificaciones por bimestre/parcial con rúbricas
12. Evaluaciones ordinarias, finales, extraordinarios (escalas SEP/UAEMEX)
13. Planeación de Clases — kanban temas, cobertura curricular
14. Comunicados con acuse digital

### FASE 3 (Completa)
15. Horarios — aSc TimeTables XML import/export, CRUD conflictos
16. Expediente Médico — incidentes, condiciones crónicas, alerta emergencia
17. Reportes de Conducta — sanciones, planes de mejora, seguimiento
18. Reportes Académicos — boletas PDF WeasyPrint NEM + UAEMEX
19. Evaluación Docente 360° — 4 tipos, 7 criterios ponderados, escala 1-5

### FASE 4 (Completa)
20. Asistente IA — NVIDIA NIM + historial persistente sesiones
21. Riesgo Académico — alertas 1297 activas; umbral reprobación/ausentismo
22. Dashboard BI — Apache Superset 6.1.0; 4 dashboards con RLS
23. Learning Paths — rutas adaptativas IA (Claude Haiku), 4 paths, 23 recursos
24. Grade Analytics — tendencias, distribución, riesgo plantel
25. Learning Analytics — patrones aprendizaje
26. Gradebook — spreadsheet masivo, calcular_calificacion_periodo(), NEE
27. Mi Progreso — vista alumno countdown tareas, badges ganados
28. Ponderación Config — esquemas por nivel/materia, suma=100%, es_nee flag

### FASE 5 (Completa)
29. Certificación Digital Ed25519 — emitir, firmar, QR, verificar público
30. Certificados verificación pública — /verificar/:folio sin auth

### FASE 6-10 (Completas)
31. Evaluaciones (programar exámenes ordinario/final/extraordinario)
32. Planeación (temas IMPARTIDO/PLANEADO/PENDIENTE)
33. Rúbricas (CRUD + criterios niveles_logro JSONB)
34. Encuestas (4 tipos: ESCALA_5, OPCION_MULTIPLE, BOOLEANO, TEXTO_LIBRE)
35. Badges (catálogo, otorgar manual, auto-evaluar por ciclo)
36. Portal Familias (vista 360°: KPIs, alertas, badges, LP, calificaciones, asistencias)
37. Grade Analytics (4 tabs: riesgo, tendencias, distribución, resumen ejecutivo)

### FASES 20-26 (Opensource Stack)
38. H5P — contenido educativo interactivo (10 tipos, iframe player, xAPI)
39. BigBlueButton — videoconferencias API-only (reuniones, grabaciones, join URL)
40. Grafana / Prometheus — observabilidad, 5 dashboards, 13 alertas
41. n8n — automatizaciones y webhooks
42. Paperless-ngx — OCR expedientes (integrado en expediente.py)
43. Biblioteca — libros + préstamos, control atómico ejemplares (Mig 084)
44. Portal Admin externo — convocatorias, información institucional

### FASES 27-31 (RRHH + Operatividad)
45. Licencias RRHH — CRUD, estados, aprobación
46. Capacitaciones RRHH — registro, evaluación, constancias
47. Disponibilidad Docente — horario disponible para aSc
48. Expediente Laboral — documentos, contratación, antigüedad
49. Asistencia Personal — pase de lista staff, justificaciones
50. Condiciones Crónicas — SB-006/007, alerta emergencia
51. Justificaciones de Falta — OA-003, aprobar/rechazar
52. Aulas — CRUD completo + disponibilidad + verificar conflicto
53. Kardex UAEMEX — constancia PDF preparatoria, ordinario/extra/definitiva
54. Estadística 911 SEP — matriz edad×grado×sexo + Sección IX discapacidad
55. Acta de Evaluación — reporte oficial por grupo
56. Admisión — inscripción nueva, generación PDF
57. Director Dashboard — KPIs generales, gráficas PrimeNG (nivel_acceso ≤ 2)
58. Monitor — telemetría JVM, Celery queues, DB metrics (nivel_acceso ≤ 2)
59. Ayuda — documentación in-app

---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|---|---|---|
| Base de datos | PostgreSQL + pgvector | 18.4 + 0.8.2 |
| PK estándar | UUID v7 uuidv7() nativo PG18 | — |
| Caché | Valkey | 9.1.0 |
| Backend BFF | Spring Boot + Java (hexagonal) | 3.x / 21 |
| Backend IA | FastAPI + SQLAlchemy + Celery | 0.136 / 2.0.50 / 5.6.3 |
| Frontend | Angular + PrimeNG | 22 / 21 |
| Auth IdP | Authentik OIDC/OAuth2 | 2026.5.2 |
| Almacenamiento | SeaweedFS (compatible S3) | — |
| BI | Apache Superset | 6.1.0 |
| Horarios | aSc TimeTables XML | — |
| PDF | WeasyPrint (Python) | — |
| Blockchain Fase 5 | web3.py + Polygon + Blockcerts | — (diferido) |
| Infra | Docker Compose Ubuntu 24 ARM OCI | — |
| Auditoría BD | Esquema auditoria triggers PL/pgSQL | 344 triggers biu activos |
| PgBouncer | Connection pooling transaction mode | 1.25.2 |
| Monitoreo | Prometheus + Grafana | — |
| Secretos | HashiCorp Vault | v7 |
| H5P | Node.js @lumieducation/h5p-server | :8091 |
| Automatización | n8n workflows | :5678 |
| Notificaciones | ntfy push notifications | :2586 |
| OCR | Paperless-ngx + Celery | — |
| Documentos | Stirling-PDF + Carbone | :8081 / :3001 |
| Servidor | ARM OCI 4 cores 24 GB RAM | — |
| Dominio dev | ades.setag.mx 129.213.35.140 | — |
| SSL | Let's Encrypt válido 2026-09-01 | — |

---

## Convenciones de Base de Datos

- Prefijo: ades_
- PK: id UUID NOT NULL DEFAULT uuidv7()
- Ref: ref UUID NOT NULL UNIQUE DEFAULT uuidv7() para business key SCD2
- Auditoría: trigger auditoria.trg_auditoria_biu en todas las tablas (344 activos)
- Soft delete: is_active BOOLEAN NOT NULL DEFAULT TRUE
- Estatus: ades_estatus + FK estatus_id UUID
- Nombres: snake_case español
- FKs: sufijo _id UUID
- Comentarios obligatorios en tablas y columnas no obvias
- row_version para concurrencia optimista
- Migraciones: db/migrations/ con prefijo 3 dígitos (hasta 094 aplicadas)

---

## Migraciones Aplicadas (resumen)

| Rango | Contenido |
|---|---|
| 001-007 | Schema base FASE 1-10 (57 tablas), seeds institucionales |
| 008-010 | Roles, parámetros sistema, cerrar_ciclo_y_promover() |
| 011-020 | Inscripciones, horarios, biblioteca, catálogos |
| 021-030 | Auditoría v2, NEE, movilidad, conducta, roles ades_app |
| 031-042 | Sanciones, planes mejora, condiciones crónicas, justificaciones |
| 043-060 | Temarios, personal no-docente, RRHH, planeación |
| 061-070 | BBB reuniones, biblioteca, MVs BI, PgBouncer |
| 071-080 | Particionamiento, memory embeddings, BI views, audit security, ades_app role |
| 081-082 | H5P (10 tipos), BigBlueButton (3 tablas) |
| 083-089 | Ciclo sistema educativo, biblioteca, campos NEM, periodos trimestres, menús UUID, catálogos edificios, eval cualitativa NEM |
| 090-094 | Menús permisos rol, fix gradebook UAEMEX, fix LP audit, classroom gaps (plagio+NEE+multimedia), dedup códigos postales |

---

## Estructura del Repositorio

```
/opt/ades/
├── .agent/         (CONTEXT.md, STATE.md, MAP.md, AGENT.md, HEURISTICS.md, RULES.md)
├── DECISIONS/      (ADRs 0001-0011)
├── db/             (migrations 001-094, seeds 001-009, scripts)
├── backend/        (FastAPI: api/v1/, models/, schemas/, services/, worker/, tests/)
├── backend-spring/ (BFF Spring Boot: 62 módulos hexagonales, 76 controllers)
├── frontend/       (Angular 22: 59 features lazy-loaded)
├── infrastructure/ (nginx, superset, grafana, h5p, vault)
├── integrations/   (asc_horarios, superset, cube)
├── memory/         (SemanticCache, LongTermMemory)
├── agents/         (subagentes del framework)
├── docs/           (manual-usuario.md 1526 líneas, plan_pruebas_integral.md)
├── data/           (volúmenes Docker — en .gitignore)
├── docker-compose.yml
└── .env            (en .gitignore)
```

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
13. NEM 1°-2° primaria: evaluación cualitativa A/B/C/D por campo formativo.
    equiv_num: A=10, B=8, C=6, D=4. Activable por plantel en ades_config.
14. Fecha de nacimiento: año válido 1900 ≤ año ≤ año_actual (ValidationUtils.java).

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

## Datos Cargados (Seeds 2026-2027)

Escuelas: 1 | Planteles: 3 | Grupos activos: 66 (78 total) | Profesores: 168
Alumnos: 1,980 | Usuarios: 2,054 | Materias: 222+ | Temas: 523+
Libros biblioteca: 60 | Préstamos: 74 | Evaluaciones 360°: 32 (escala 1-5 correcta)

Seeds aplicados: 001 (institución), 002 (usuarios/alumnos), 003 (académico),
004 (calificaciones), 005 (portal convocatorias), 006 (gradebook),
007 (planes ponderación), 008 (biblioteca), 009 (eval-docente 360°)

---

## Datos de Infraestructura (2026-06-23)

| Servicio | Estado | Puerto interno | Notas |
|---|---|---|---|
| PostgreSQL 18 | ✅ healthy | 5432 | Migraciones 001-094 aplicadas |
| PgBouncer | ✅ healthy | 6432 | transaction mode, SCRAM-SHA-256 |
| Valkey 9.1.0 | ✅ healthy | 6379 | caché + Celery broker |
| SeaweedFS | ✅ healthy | 9000 (S3), 8888 (Filer) | archivos, entregas, boletas |
| Authentik 2026.5.2 | ✅ healthy | 9010 | IdP OIDC; ADES Frontend app configurada |
| Spring BFF (ades-bff) | ✅ running | 8080 | 62 módulos, 76 controllers |
| FastAPI (ades-api) | ✅ healthy | 8000 | IA + docs; 180+ ops REST |
| Angular Frontend | ✅ healthy | 4200 | 59 features lazy-loaded |
| nginx | ✅ running | 80/443 | TLS Let's Encrypt 2026-09-01 |
| Superset | ✅ healthy | 8088 | 4 dashboards con RLS por plantel |
| Prometheus | ✅ healthy | 9090 | scraping api+bff+pg+pgbouncer |
| Grafana | ✅ healthy | 3003 | 5 dashboards |
| H5P Node | ✅ healthy | 8091 | 10 tipos contenido interactivo |
| n8n | ✅ starting | 5678 | automatizaciones webhooks |
| ntfy | ✅ healthy | 2586 | notificaciones push |
| Paperless-ngx | ✅ healthy | — | OCR expedientes |
| Stirling-PDF | ✅ healthy | 8081 | manipulación PDF |
| Carbone | ✅ healthy | 3001 | generación documentos plantilla |
| Flowise | ✅ healthy | 3002 | builder flows IA |
| Vault | ✅ running | 8200 | secretos, DATABASE_URL, tokens |
| Celery worker/beat | ✅ running | — | boletas batch, alertas nocturnas, refresh MVs |
| Flower | ✅ running | 5555 | monitor Celery |

---

## Prioridades de Desarrollo Pendientes

### Bloqueantes (corregidos 2026-06-23):
- [x] ADV-02/03: Validación año fecha_nacimiento en ValidationUtils.java → AdminController

### Alta prioridad:
- [ ] Google Workspace SSO (esperando credenciales OAuth2 del plantel)
- [ ] Paperless-ngx: completar UI expediente-doc con OCR búsqueda
- [ ] Verificar e2e tests con cambios recientes (cascada, classroom gaps)

### Media prioridad:
- [ ] Hexagonal Spring BFF: completar ~12 módulos restantes sin ApplicationService
- [ ] Comentarios BD: `db/migrations/068_comentarios_schema.sql` (COMMENT ON TABLE/COLUMN)
- [ ] ER Diagram en Mermaid en docs/

### Diferidas:
- [ ] FASE 5B — Blockchain Polygon PoS
- [ ] POSTGRES_USER: ades_admin → ades_app (ventana mantenimiento)
- [ ] HashiCorp Vault: integración completa secretos FastAPI (actualmente os.environ)
- [ ] Partición ciclo_2029_2030 (crear antes de agosto 2029)
- [ ] BigBlueButton: configurar BBB_SERVER_URL + BBB_SHARED_SECRET (servidor pendiente)
- [ ] H5P: descargar h5p-core files en volumen /data/h5p-core/
