# ADES — Sistema Integral de Administración Escolar

[![Python](https://img.shields.io/badge/Python-3.12-brightgreen)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.136+-blue)](https://fastapi.tiangolo.com/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-22-red)](https://angular.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18+pgvector-336791)](https://www.postgresql.org/)
[![Valkey](https://img.shields.io/badge/Valkey-9.1.0-blueviolet)](https://valkey.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)](https://www.docker.com/)
[![Licencia](https://img.shields.io/badge/Licencia-Privado-red)](#licencia)

---

## Descripción General

**ADES** es el sistema integral de administración escolar del **Instituto Nevadi** (México), institución educativa privada con tres planteles y tres niveles educativos: Primaria (SEP), Secundaria (SEP) y Preparatoria (UAEMEX).

El sistema cubre la operación completa de la institución: estructura académica, inscripciones y reinscripciones, asignación docente, calificaciones, asistencias, planificación curricular, expedientes médicos, conducta estudiantil, horarios, recursos humanos, comunicación institucional, certificación digital, expediente documental OCR, análisis predictivo de riesgo académico con IA, contenido educativo interactivo H5P, videoconferencias BigBlueButton, y dashboards BI con Apache Superset.

La interfaz está construida bajo el estilo empresarial de Oracle APEX: interactive grids, master-detail, LOV (list of values) y edición inline directa sobre tablas. La arquitectura backend sigue el patrón hexagonal (puertos y adaptadores) con un BFF Spring Boot que orquesta los módulos de dominio.

### Contexto Institucional

| Campo | Valor |
|-------|-------|
| **Razón social** | Instituto Nevadi |
| **Eslogan** | "EL ÚNICO CAMINO PARA SALIR ADELANTE ES LA EDUCACIÓN" |
| **Sitio** | https://institutonevadi.edu.mx/ |
| **Color institucional** | `#C41724` |
| **Planteles** | Metepec · Tenancingo · Ixtapan de la Sal (Estado de México) |
| **Ciclo SEP** | 2026-2027 (Primaria y Secundaria) |
| **Ciclo UAEMEX** | 26B / 27A (Preparatoria) |
| **Servidor** | `ades.setag.mx` · 129.213.35.140 · ARM OCI · 4 cores · 24 GB RAM |

### Escala del Sistema (Ciclo 2026-2027)

| Métrica | Valor |
|---------|-------|
| **Planteles activos** | 3 |
| **Grados** | 39 |
| **Grupos activos** | 66 |
| **Profesores** | 168 |
| **Alumnos** | 1,980 |
| **Usuarios totales** | 3,483 (alumnos + padres + personal) |
| **Materias en planes de estudio** | 63 — 7 Primaria NEM · 5 Secundaria NEM · 51 Preparatoria CBU 2024 |
| **Temas curriculares** | 1,200+ (NEM Primaria 648 · Secundaria NEM 60 · CBU 2024 408 · Nevadi 56+) |
| **Calificaciones** | 76,320 |
| **Asistencias** | 180,000+ |
| **Tablas PostgreSQL** | 164 |
| **Migraciones SQL** | 84 scripts (`001–082` + scripts fechados) |
| **Roles del sistema** | 18 |
| **Módulos Angular** | 55+ (lazy-loaded por ruta) |
| **Endpoints REST** | 200+ (FastAPI) |
| **Suites E2E** | 17 (Playwright) |
| **ADRs registrados** | 10 |

---

## Arquitectura Técnica

ADES tiene dos backends que se complementan: un **BFF Spring Boot** (Backend for Frontend) con arquitectura hexagonal que cubre los 57 módulos de dominio principal, y un **backend FastAPI** asincrónico que atiende las operaciones especializadas de alto rendimiento. Nginx enruta las solicitudes a uno u otro según el prefijo del endpoint, de forma transparente para el cliente Angular.

### Diagrama de Componentes

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Clientes                                      │
│   Angular SPA (ades.setag.mx)     Portal Externo (portal :4201)      │
│   PrimeNG · Signals · Oracle APEX style                               │
└──────────────────────────┬───────────────────────────────────────────┘
                           │ HTTPS / JWT RS256
┌──────────────────────────▼───────────────────────────────────────────┐
│       Nginx — Reverse Proxy · TLS Let's Encrypt · 6 dominios         │
│   ades.setag.mx · auth.ades.setag.mx · bi.ades.setag.mx             │
│   monitor.ades.setag.mx · notify.ades.setag.mx                      │
│                                                                      │
│   Routing rules:                                                     │
│   /api/v1/(ai|chatbot|carbone|pdf|webhooks|automations|              │
│            push|expediente|certificados|h5p|bbb)  →  FastAPI :8000  │
│   /api/**  (resto de endpoints de dominio)        →  BFF :8080      │
│   /h5p/    (player H5P)                           →  H5P Node :8091 │
│   /        (frontend)                             →  Angular :4200  │
└──────────┬────────────────────┬─────────────────────────────────────┘
           │                    │
┌──────────▼───────┐  ┌─────────▼───────────────────────────────────┐
│  Spring Boot BFF │  │         FastAPI (ades-api :8000)             │
│  :8080           │  │  Python 3.12 · SQLAlchemy 2.x · Pydantic v2  │
│                  │  │                                              │
│  Hexagonal       │  │  Módulos especializados:                     │
│  Ports & Adapters│  │  · IA: Claude/NVIDIA NIM, alertas, LP       │
│  57 módulos de   │  │  · OCR: Paperless-ngx + GIN FTS             │
│  dominio:        │  │  · PDF: WeasyPrint boletas, Stirling-PDF     │
│  · alumnos       │  │  · Certificados: Ed25519 + QR               │
│  · profesores    │  │  · H5P: subida paquetes, xAPI               │
│  · calificaciones│  │  · BBB: API checksum SHA-1                  │
│  · asistencias   │  │  · Push: ntfy SSE                           │
│  · horarios      │  │  · Automations: n8n webhooks                │
│  · conducta      │  │  · Chatbot: Flowise NL→SQL                  │
│  · RRHH          │  │                                              │
│  · comunicados   │  │  Celery Workers  (OCR, boletas, alertas)    │
│  · y 49 más…     │  │  Celery Beat     (refresh MVs, comunicados) │
│                  │  │  Flower          :5555 (monitoreo colas)    │
│  JdbcTemplate    │  └──────────────────────────────────────────────┘
│  + JPA           │
│  → PgBouncer     │
└──────────┬───────┘
           │ Ambos backends leen/escriben en la misma BD
┌──────────▼──────────────────────────────────────────────────────────┐
│                       Capa de Datos                                  │
│                                                                      │
│  ┌──────────────┐  ┌───────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ PostgreSQL 18│  │  Valkey   │  │  SeaweedFS   │  │ PgBouncer │  │
│  │ + pgvector   │  │  9.1.0    │  │  (S3-compat) │  │ :6432     │  │
│  │ 150+ tablas  │  │  Sesiones │  │  Archivos    │  │ pool 25   │  │
│  │ UUID v7 PKs  │  │  Celery   │  │  Entregas    │  │ SCRAM-256 │  │
│  │ audit trail  │  │  cache    │  │  Documentos  │  │ tx mode   │  │
│  └──────────────┘  └───────────┘  └──────────────┘  └───────────┘  │
└──────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────┐
│                     Servicios Integrados                              │
│                                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐  │
│  │  Authentik  │  │   Superset  │  │   Grafana   │  │   Vault   │  │
│  │ 2026.5.2    │  │   6.1.0 BI  │  │ Prometheus  │  │ Secrets   │  │
│  │ OIDC / MFA  │  │ 4 dashboards│  │ 5 dashboards│  │ rotación  │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐  │
│  │   Carbone   │  │ Stirling-PDF│  │   Flowise   │  │   ntfy    │  │
│  │  DOCX→PDF   │  │ OCR · Merge │  │ NL→SQL · AI │  │ Push SSE  │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐  │
│  │ Paperless   │  │   H5P Node  │  │   n8n       │  │   BBB     │  │
│  │ OCR ngx     │  │ :8091 xAPI  │  │ Automatiz.  │  │ Videoconf │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### División de Responsabilidades FastAPI vs BFF

| Criterio | FastAPI (ades-api :8000) | Spring Boot BFF (:8080) |
|----------|--------------------------|-------------------------|
| **Lenguaje** | Python 3.12 | Java 21 |
| **Framework** | FastAPI + SQLAlchemy async | Spring Boot 3.x + JdbcTemplate/JPA |
| **Patrón** | Servicios asíncronos de alta carga | Hexagonal (Ports & Adapters), 57 módulos |
| **Endpoints** | `/api/v1/ai`, `/chatbot`, `/carbone`, `/pdf`, `/expediente`, `/certificados`, `/h5p`, `/bbb`, `/push`, `/webhooks`, `/automations` | Todo `/api/v1/*` restante (alumnos, calificaciones, horarios, RRHH, comunicados, etc.) |
| **Persistencia** | SQLAlchemy 2.x async + asyncpg | JdbcTemplate (queries SQL) + Spring Data JPA |
| **Pool BD** | PgBouncer :6432 (statement_cache_size=0) | PgBouncer :6432 (prepareThreshold=0) |
| **Secretos** | Vault via `os.environ` + `.env` | Spring Cloud Vault |
| **Observabilidad** | Starlette Prometheus `/metrics` | Micrometer `/actuator/prometheus` |
| **Especializaciones** | IA, OCR, PDF, firma criptográfica, xAPI | Lógica de dominio, validaciones, RBAC scope plantel |

---

## Stack Tecnológico

| Capa | Tecnología | Versión | Puerto interno | Propósito |
|------|-----------|---------|---------------|-----------|
| **Base de datos** | PostgreSQL + pgvector | 18 | 5432 | Persistencia ACID, embeddings semánticos, auditoría |
| **Connection pool** | PgBouncer | 1.25.2 | 6432 | Transaction mode, SCRAM-SHA-256, pool=25 |
| **Caché / Sesiones** | Valkey | 9.1.0 | 6379 | Sesiones, cola Celery, semántica corta |
| **Almacenamiento** | SeaweedFS | latest | 8888/9000 | Distribuido S3-compat — archivos, entregas, documentos |
| **IdP / SSO** | Authentik | 2026.5.2 | 9010/9443 | OIDC/OAuth2, MFA TOTP, cuentas locales |
| **Gestión de secretos** | HashiCorp Vault | latest | 8200 | PKI, llaves privadas, rotación de credenciales |
| **Backend API** | FastAPI + SQLAlchemy | 0.136+ / 2.0 | 8000 | Asincrónico, Pydantic v2, OpenAPI docs |
| **BFF Hexagonal** | Spring Boot | 3.x | 8080 | 57 módulos de dominio con puertos y adaptadores |
| **Tareas async** | Celery + Valkey | 5.6+ | — | Background jobs, OCR, notificaciones, refresh MVs |
| **Monitor Celery** | Flower | latest | 5555 | Monitoreo visual de colas de tareas |
| **Runtime backend** | Python | 3.12 | — | LTS — `python:3.12-slim-bookworm` |
| **Frontend SPA** | Angular + PrimeNG | 22 | 4200 | Estilo APEX: interactive grids, master-detail, LOV |
| **Portal Externo** | Angular | 22 | 4201 | Convocatorias y preinscripción pública |
| **BI / Dashboards** | Apache Superset | 6.1.0 | 8088 | 4 dashboards, 4 RLS por plantel, guest tokens |
| **Métricas** | Prometheus + Grafana | latest | 9090/3003 | 5 dashboards — API, JVM BFF, PgBouncer, PostgreSQL |
| **Automatización** | n8n | latest | 5678 | Webhooks académicos, batch boletas, alertas |
| **Push notifications** | ntfy | latest | 2586 | SSE nativo — browser y móvil, sin Firebase |
| **PDF avanzado** | Stirling-PDF | latest | 8081 | Merge, marca de agua, OCR, compresión |
| **Generador reportes** | Carbone | latest | 3001 | Plantillas DOCX/XLSX → PDF: boletas, constancias, kardex |
| **AI Chatbot** | Flowise | latest | 3002 | NL→SQL con Claude Haiku o NVIDIA NIM |
| **Documentos OCR** | Paperless-ngx | latest | — | OCR en español, búsqueda full-text, etiquetado |
| **Contenido H5P** | H5P Node.js | latest | 8091 | Quizzes, videos interactivos, xAPI → `ades_h5p_resultados` |
| **Videoconferencias** | BigBlueButton | API-only | — | Reuniones, asesorías, clases virtuales — checksum SHA-1 |
| **Horarios K-12** | aSc TimeTables | ext. | — | Motor NP-difícil especializado, import/export XML |
| **IA / Agente** | LangChain + Claude API | latest | — | Asistente pedagógico, riesgo académico, NL→SQL |
| **Exportadores** | postgres_exporter + pgbouncer_exporter | latest | 9187/9127 | Métricas BD para Prometheus |
| **Proxy** | Nginx | alpine | 80/443 | TLS, enrutamiento, proxy WebSocket |
| **SO** | Ubuntu Server | 24 LTS | — | ARM64 — OCI Always Free |

### Servicios Docker Compose (estado productivo)

| Contenedor | Imagen | Puerto | Estado |
|-----------|--------|--------|--------|
| `ades-postgres` | pgvector/pgvector:pg18 | 5432 | healthy |
| `ades-valkey` | valkey/valkey:9.1.0 | 6379 | healthy |
| `ades-pgbouncer` | edoburu/pgbouncer | 6432 | healthy |
| `ades-seaweedfs` | chrislusf/seaweedfs | 8888/9000/9333 | healthy |
| `ades-authentik-server` | ghcr.io/goauthentik/server:2026.5.2 | 9010/9443 | healthy |
| `ades-authentik-worker` | ghcr.io/goauthentik/server:2026.5.2 | — | healthy |
| `ades-vault` | hashicorp/vault | 8200 | running |
| `ades-api` | (build local) | 8000 | healthy |
| `ades-bff` | (build local) | 8080 | running |
| `ades-celery-worker` | (mismo que api) | — | running |
| `ades-celery-beat` | (mismo que api) | — | running |
| `ades-celery-flower` | (mismo que api) | 5555 | running |
| `ades-frontend` | (build local) | 4200 | running |
| `ades-portal` | (build local) | 4201 | running |
| `ades-superset` | (build local) | 8088 | healthy |
| `ades-carbone` | (build local) | 3001 | healthy |
| `ades-flowise` | flowiseai/flowise | 3002 | healthy |
| `ades-ntfy` | binwiederhier/ntfy | 2586 | healthy |
| `ades-stirling-pdf` | frooodle/s-pdf | 8081 | healthy |
| `ades-prometheus` | prom/prometheus | 9090 | healthy |
| `ades-grafana` | grafana/grafana-oss | 3003 | healthy |
| `ades-n8n` | n8nio/n8n | 5678 | healthy |
| `ades-paperless` | ghcr.io/paperless-ngx | — | running |
| `ades-h5p` | ades-h5p (Node.js) | 8091 | healthy |
| `ades-postgres-exporter` | quay.io/prometheuscommunity | 9187 | running |
| `ades-pgbouncer-exporter` | prometheuscommunity | 9127 | running |
| `ades-nginx` | nginx:alpine | 80/443 | running |
| `ades-certbot` | certbot/certbot | — | running |

---

## Dominios del Sistema (DDD)

| Dominio | Entidades principales |
|---------|-----------------------|
| **1. Identidad Institucional** | Instituto, Planteles, Identidad visual, Catálogo SEPOMEX, Parámetros del sistema |
| **2. Estructura Académica** | Niveles, Grados, Grupos, Materias, Planes NEM/CBU 2024, Temas curriculares, Ciclos, Calendarios |
| **3. Población Escolar** | Alumnos, Padres/tutores, Profesores, Inscripciones, Reinscripciones, Movilidad estudiantil |
| **4. Operación Académica** | Calendario, Horarios aSc, Asistencias, Calificaciones, Gradebook, Planeación de clases, Evaluaciones, Tareas, Rúbricas |
| **5. Comunicación** | Comunicados, Acuses digitales, Notificaciones in-app, Foros por materia/tutoría, Push SSE |
| **6. Salud y Bienestar** | Expediente médico, Incidentes, Alergias, Medicamentos, Condiciones crónicas, Contacto emergencia |
| **7. Conducta y Disciplina** | Reportes, Sanciones disciplinarias, Planes de mejora, Seguimiento de compromisos |
| **8. Recursos Humanos** | Licencias, Capacitaciones, Expediente laboral, Asistencia personal, Disponibilidad docente |
| **9. Reportes y Analítica** | Boletas PDF WeasyPrint, Carbone DOCX→PDF, Superset BI, KPIs, Predicción de riesgo |
| **10. Inteligencia Artificial** | Asistente pedagógico, Rúbricas automáticas, Learning paths adaptativas, Análisis de abandono |
| **11. Documentos Digitales** | Expediente digital OCR Paperless-ngx, Certificados Ed25519, Firma digital, QR verificación |
| **12. Admisión Externa** | Portal público convocatorias, Pre-inscripción, Catálogo de carreras y planteles |

---

## Módulos Implementados

### Gestión Académica Core

| Módulo | Descripción |
|--------|-------------|
| **Identidad Institucional** | Datos del instituto, logos, colores, parámetros del sistema (18 params en 5 grupos: GENERAL, CONTACTO, APARIENCIA, SEP, FUNCIONALIDAD), historial de identidad |
| **Catálogo Geográfico** | SEPOMEX completo — estados, municipios, localidades, códigos postales, tipos de asentamiento |
| **Estructura Académica** | Niveles, grados, grupos (A/B), ciclos escolares SEP 2026-2027 y UAEMEX 26B/27A, calendarios, períodos de evaluación |
| **Planes de Estudio** | NEM Primaria (648 temas / 6 grados), NEM Secundaria (60 temas / 3 grados), CBU 2024 UAEMEX (408 temas / 51 materias), 7 materias institucionales Nevadi; mapa curricular visual con edición inline |
| **Alumnos** | Alta, filiación, CURP, matrícula, histórico de inscripciones, contactos familiares, importación CSV/Excel |
| **Profesores** | Registro, asignación materia↔grupo, disponibilidad horaria, reasignación, importación CSV |
| **Inscripciones y Reinscripciones** | Flujo completo con verificación de no-adeudo (PE-016), portal de reinscripción con confirmación/rechazo del padre, workflow de cierre de ciclo y promoción automática |
| **Usuarios y Autenticación** | 18 roles, OIDC Authentik, JWT RS256, MFA TOTP/WebAuthn para administradores, cuentas locales |

### Operación Académica

| Módulo | Descripción |
|--------|-------------|
| **Calificaciones** | Libreta bimestral/parcial, edición inline por celda, guardado bulk, ajuste manual con justificación (≥20 chars), optimistic locking |
| **Gradebook** | Panel spreadsheet actividades × alumnos, drawer de calificación, función PG `calcular_calificacion_periodo()` con 3 triggers, escalas dinámicas SEP (0-10) / UAEMEX (0-100) |
| **Asistencias** | Registro por clase, estados: PRESENTE / AUSENTE / TARDE / JUSTIFICADA, alertas de ausentismo <80%, importación CSV |
| **Tareas y Entregas** | CRUD actividades, subida de archivos a SeaweedFS, calificación con rúbrica, generación automática de slots al cargar plan de estudio |
| **Ponderaciones** | Esquemas por nivel/materia, validación suma=100%, historial de versiones, efectivo por materia (materia > genérico nivel) |
| **Mi Progreso (alumno)** | Cards por materia con % progreso, tareas pendientes con countdown, subida de archivos, historial de ítems |
| **Evaluaciones** | Agenda de exámenes ORDINARIO/FINAL/EXTRAORDINARIO, libreta bulk save, estadísticas por evaluación |
| **Planeación de Clases** | Temas con estados PLANEADO/IMPARTIDO/PENDIENTE, cobertura curricular, avance por materia |
| **Horarios** | Grid semanal por grupo/docente, export/import XML para aSc TimeTables, view de conflictos |
| **Grupos y Aulas** | CRUD con disponibilidad horaria, capacidad, tipo de aula, vista de conflictos por hora/día |

### Comunicación y Comunidad

| Módulo | Descripción |
|--------|-------------|
| **Comunicados** | Rich text, tipos OFICIAL/INFORMATIVO/URGENTE, acuse digital, recurrentes con `proximo_envio`, reporte de lectura |
| **Notificaciones** | Campanita en topbar, badge conteo, popover últimas 10, marcar leída/todas, ntfy SSE para browser/móvil |
| **Foros** | Por materia y tutoría, tipos: GENERAL / TAREA / ANUNCIO / DUDA, moderación de contenido, foros por grupo |
| **Portal de Padres** | KPIs hijo, calificaciones por período, asistencias, tareas, comunicados, badges obtenidos |

### Salud, Conducta y Bienestar

| Módulo | Descripción |
|--------|-------------|
| **Expediente Médico** | Alergias, medicamentos, incidentes, personal de salud, certificados de aptitud PDF |
| **Condiciones Crónicas** | Registro por alumno, alerta de emergencia con contacto, medicación en el plantel |
| **Conducta** | Reportes de incidente, sanciones formales (Director-only), planes de mejora con compromisos JSONB (alumno/padre/escuela), seguimiento con avance codificado |
| **Justificaciones** | Solicitud por padre/tutor, flujo aprobación/rechazo, vinculación con asistencias |

### Recursos Humanos Docente

| Módulo | Descripción |
|--------|-------------|
| **Licencias y Permisos** | Tipos de licencia, workflow PENDIENTE→APROBADA/RECHAZADA, notificación RH |
| **Capacitaciones** | Registro con hrs crédito, validación RH, resumen de horas por docente |
| **Expediente Laboral** | Datos de contratación, documentos digitales vinculados a Paperless-ngx |
| **Asistencia Personal** | Bitácora de asistencia del personal administrativo y docente |
| **Disponibilidad Docente** | Franjas horarias disponibles por día, restricciones para aSc TimeTables |

### Inteligencia Artificial y Analytics

| Módulo | Descripción |
|--------|-------------|
| **Asistente Pedagógico** | Chat con Claude Sonnet 4.6 / NVIDIA NIM, contexto plantel/ciclo, persistencia de historial de sesiones (IA-015) |
| **Alertas de Riesgo** | Detección automática: reprobación <6.0 / 60, ausentismo <80%, niveles BAJO/MEDIO/ALTO/CRITICO |
| **Learning Paths** | Rutas de refuerzo adaptativas con recursos, asignación por alumno, recomendación IA (Claude Haiku), ajuste dinámico por progreso |
| **Grade Analytics** | Tendencias por período, distribución de calificaciones, riesgo de reprobación, resumen ejecutivo, exportación |
| **Predicción de Abandono** | Modelo IA sobre historial académico: asistencia, calificaciones, conducta, tareas — score y factores de riesgo |
| **AI Chatbot NL→SQL** | Flowise + Vanna AI — consultas en lenguaje natural sobre `ades_*`, con RLS por rol |
| **Detección Bullying** | Escaneo semántico de encuestas con IA para detectar patrones de acoso |

### Reportes y Documentos

| Módulo | Descripción |
|--------|-------------|
| **Boletas PDF** | WeasyPrint + Jinja2, template institucional (color Nevadi, logo, firmas), generación individual y batch ZIP |
| **Reportes Carbone** | Microservicio Node.js — plantillas DOCX/XLSX → PDF, boletas, constancias, kardex, horarios |
| **Stirling-PDF** | Fusión de boletas grupo, marca de agua institucional, compresión, OCR sobre documentos subidos |
| **Certificados Digitales** | Firma Ed25519 (clave privada en Vault), hash SHA-256 encadenado, QR código de verificación embebido en PDF, portal público `/verificar/:folio` sin autenticación |
| **Expediente Digital** | Documentos por alumno en Paperless-ngx, OCR asíncrono en español vía Celery, búsqueda full-text GIN sobre `ocr_texto`, visualizador PDF embebido |
| **BI Superset** | 4 dashboards: Instituto / Plantel / Docente / Alumno — 7 charts, 4 RLS por `plantel_id`, guest tokens Angular |

### Infraestructura y Operación

| Módulo | Descripción |
|--------|-------------|
| **Dashboard** | KPIs en tiempo real, distribución por nivel, gráfico CSS barras, accesos rápidos, widgets configurables por usuario |
| **Monitor** | Telemetría del sistema: JVM BFF, Celery queues, PgBouncer pool, disco — 5 dashboards Grafana |
| **Admin Global** | Gestión de ciclos, planteles, grupos, usuarios, parámetros del sistema — Interactive Grid APEX-style |
| **Cierre de Ciclo** | Función `cerrar_ciclo_y_promover()` — inscripción automática ciclo siguiente, gestión de REPROBADO/EGRESADO/BAJA |
| **Auditoría** | 150+ tablas con `audit_biu` BEFORE INSERT/UPDATE, columnas canónicas, `ades_app` role no-superusuario |
| **n8n Automatización** | Webhooks FastAPI → n8n: alerta asistencia, cierre período, nuevo comunicado, batch boletas, recordatorios |
| **Push Notifications** | `PushNotificationService` Angular, SSE ntfy, notificaciones nativas del browser |
| **Encuestas** | ESCALA_5, OPCION_MULTIPLE, BOOLEANO, TEXTO_LIBRE; resultados estadísticos; toggle activa/inactiva |
| **Badges y Gamificación** | Catálogo 8+ insignias, auto-evaluación por métricas (asistencia/promedio/conducta), otorgamiento manual, vista galería alumno |
| **Movilidad Estudiantil** | Baja temporal, reingreso, cambio de plantel — con validación de fechas y motivo |

### Contenido Educativo y Videoconferencias

| Módulo | Descripción |
|--------|-------------|
| **H5P Interactivo** | Servicio Node.js `@lumieducation/h5p-server`, 10 tipos de contenido, subida de paquetes `.h5p`, player iframe con sanitización Angular, resultados xAPI en `ades_h5p_resultados`, asignación a grupos |
| **BigBlueButton** | Integración API-only con checksum SHA-1, listado y creación de salas, URL de acceso mod/asistente, gestión de grabaciones, webhook receptor |

### Portal Externo de Admisión

Portal Angular independiente en `ades-portal` (puerto 4201) orientado al público general:

- Presentación del Instituto Nevadi — misión, visión, oferta educativa
- Catálogo de convocatorias activas con fechas y requisitos
- Formulario de pre-inscripción con validación
- Información por plantel y nivel educativo
- Diseño institucional accesible, sin autenticación requerida

---

## Autenticación y Control de Acceso

### Flujo OIDC/PKCE

```
Browser → /login
  └─→ Authentik (auth.ades.setag.mx)
        └─→ PKCE Authorization Code Flow
              └─→ JWT RS256 access_token
                    └─→ FastAPI verify_token() async (JWKS cache 5 min)
                          └─→ AdesUser(id, rol, plantel_id, nivel_acceso)
```

- Tokens almacenados en `sessionStorage` con clave `ades_token`
- `get_ades_user` propaga UUID real del usuario a `request.state` para audit trail
- MFA TOTP/WebAuthn obligatorio para grupo `ADES Admins` (Directores+)
- Route guards Angular: `authGuard` + `roleGuard(nivel)` en todas las rutas sensibles

### Roles del Sistema (18 roles)

| Nivel | Rol | Alcance |
|:-----:|-----|---------|
| 0 | `ADMIN_GLOBAL` | Todos los planteles y niveles |
| 1 | `ADMIN_PLANTEL` | Un plantel completo |
| 2 | `DIRECTOR` | Por nivel educativo dentro del plantel (hasta 3 por plantel) |
| 2 | `SUBDIRECTOR` | Suplente del director |
| 2 | `COORDINADOR_ADMINISTRATIVO` | Procesos administrativos por plantel/nivel |
| 2 | `COORDINADOR_RH` | Personal docente y administrativo |
| 2 | `COORDINADOR_AREA` | Global — coordina un área académica en todos los planteles |
| 3 | `COORDINADOR_ACADEMICO` | Coordinación académica por nivel dentro del plantel |
| 3 | `TUTOR` | Seguimiento académico personalizado de un grupo de estudiantes |
| 3 | `ORIENTADOR` | Orientación educativa y vocacional (Sec/Prep) |
| 3 | `SECRETARIA_ACADEMICA` | Expedientes, certificados, actas |
| 4 | `DOCENTE` | Sus grupos y materias asignadas |
| 4 | `MEDICO_ESCOLAR` | Expedientes médicos de su plantel |
| 4 | `PREFECTO` | Disciplina, supervisión |
| 4 | `APOYO_ACADEMICO` | Recursos, biblioteca, laboratorio |
| 4 | `APOYO_ADMINISTRATIVO` | Trámites, archivo, atención |
| 5 | `ALUMNO` | Su propio expediente y materias |
| 5 | `PADRE_FAMILIA` | Expedientes de sus hijos |

**Áreas Académicas** (`ades_areas_academicas`, 8 globales): Matemáticas, Español, Inglés, Ciencias, Historia y Geografía, Formación Cívica, Educación Física, Tecnología. El `COORDINADOR_AREA` tiene alcance transversal a todos los planteles para su área.

---

## Base de Datos

### Convenciones de Diseño

| Convención | Valor |
|-----------|-------|
| **Prefijo tablas** | `ades_` |
| **Primary Key** | `id UUID NOT NULL DEFAULT uuidv7()` — time-ordered, sin fragmentación de índice |
| **Business Key** | `ref UUID NOT NULL UNIQUE DEFAULT uuidv7()` — clave externa / SCD2 |
| **Auditoría BEFORE** | `auditoria.fn_auditoria_biu()` — gestiona `ref`, `row_version`, timestamps, `usuario_creacion/modificacion` |
| **Auditoría AFTER** | `auditoria.fn_auditoria_aiud()` — log completo en `auditoria.log_auditoria` (solo producción) |
| **Soft delete** | `is_active BOOLEAN NOT NULL DEFAULT TRUE` |
| **Estatus** | FK `estatus_id UUID → ades_estatus(id)` |
| **Concurrencia** | `row_version INTEGER DEFAULT 1` (optimistic locking — 409 en PATCH) |
| **Nombres** | `snake_case`, español, descriptivos |
| **FK columns** | sufijo `_id UUID REFERENCES tabla(id)` |

### UUID v7 — Claves Time-Ordered

Los IDs son time-ordered: los primeros 48 bits codifican el timestamp en ms, garantizando orden de inserción sin fragmentar el índice B-tree.

```
019e8f74-d142-7c91-8b82-c84464113dad   ← Plantel Metepec
019e8f74-d143-7368-a0b6-06cc2fbc7156   ← Plantel Tenancingo
019e8f74-d143-740c-aa16-63a83c575d92   ← Plantel Ixtapan de la Sal
```

### Esquema de Auditoría (implementado en `038_auditoria_v2.sql`)

| Elemento | Descripción |
|----------|-------------|
| `auditoria.log_auditoria` | PK UUID, hash MD5 encadenado, TIMESTAMPTZ — registro inmutable |
| `auditoria.fn_auditoria_biu()` | BEFORE INSERT/UPDATE — `ref`, `row_version`, timestamps, usuario |
| `auditoria.fn_auditoria_aiud()` | AFTER INSERT/UPDATE/DELETE — graba en `log_auditoria` |
| `auditoria.asignar_biu(tabla)` | Aplica solo `audit_biu` — usar en migraciones DEV |
| `auditoria.asignar_triggers(tabla)` | Aplica `audit_biu` + `audit_aiud` — usar en producción |
| `auditoria.reporte_cobertura()` | Cobertura de triggers por tabla (150+ tablas cubiertas) |

El rol `ades_app` no tiene permiso de `DELETE` sobre `auditoria.log_auditoria` — solo `ades_admin` puede hacerlo a nivel de base de datos.

### Tablas por Dominio

```
── Institucional ──────────────────────────────────────────────────────
ades_escuelas              ades_planteles           ades_parametros_sistema
ades_identidad_institucional ades_historico_identidad ades_informacion_escuela
ades_paises                ades_estados             ades_municipios
ades_localidades           ades_codigos_postales    ades_tipos_asentamiento

── Académica ──────────────────────────────────────────────────────────
ades_niveles_educativos    ades_grados              ades_grupos
ades_plantel_niveles       ades_ciclos_escolares    ades_periodos_evaluacion
ades_calendario_escolar    ades_materias            ades_materias_plan
ades_temas                 ades_aulas               ades_areas_academicas
ades_coordinaciones_area   ades_esquemas_ponderacion ades_items_ponderacion

── Personas ───────────────────────────────────────────────────────────
ades_personas              ades_profesores          ades_estudiantes
ades_inscripciones         ades_contactos_familiares ades_telefonos
ades_correos_electronicos  ades_direcciones         ades_asignaciones_docentes
ades_disponibilidad_docente ades_movilidad_estudiantil

── Operación Académica ────────────────────────────────────────────────
ades_asistencias           ades_calificaciones_periodo ades_evaluaciones
ades_calificaciones_evaluaciones ades_tareas         ades_tareas_entregas
ades_clases                ades_horarios            ades_planeacion_clases
ades_avance_planificacion  ades_archivos            ades_bajas
ades_reinscripcion_ciclo   ades_promociones_pendientes

── Comunicación ───────────────────────────────────────────────────────
ades_comunicados           ades_acuses_comunicado   ades_notificaciones
ades_foros                 ades_foro_mensajes       ades_foro_moderaciones

── Usuarios y Seguridad ───────────────────────────────────────────────
ades_usuarios              ades_roles               ades_estatus
ades_permisos              ades_llaves_firma        ades_ai_conversaciones

── Salud y Bienestar ──────────────────────────────────────────────────
ades_expedientes_medicos   ades_incidentes_medicos  ades_personal_salud
ades_condiciones_cronicas  ades_medicamentos_plantel

── Conducta y Disciplina ──────────────────────────────────────────────
ades_reportes_conducta     ades_sanciones_disciplinarias
ades_planes_mejora         ades_seguimiento_plan

── Recursos Humanos ───────────────────────────────────────────────────
ades_licencias_personal    ades_capacitaciones_docente
ades_expediente_laboral    ades_asistencia_personal
ades_justificaciones_falta

── Reportes, BI y IA ──────────────────────────────────────────────────
ades_rubricas              ades_rubrica_criterios   ades_reportes_academicos
ades_alertas_academicas    ades_learning_paths      ades_lp_recursos
ades_lp_asignaciones       ades_lp_progreso         ades_encuestas
ades_encuesta_preguntas    ades_encuesta_respuestas ades_badges
ades_badge_otorgados       ades_criterios_eval_docente ades_evaluacion_docente

── H5P y BBB ──────────────────────────────────────────────────────────
ades_h5p_tipos             ades_h5p_contenidos      ades_h5p_asignaciones
ades_h5p_resultados        ades_bbb_reuniones       ades_bbb_grabaciones
ades_bbb_asistencia

── Certificados y Documentos ──────────────────────────────────────────
ades_certificados          ades_constancias         ades_solicitudes_tramites
ades_archivos_expediente

── Auditoría ──────────────────────────────────────────────────────────
auditoria.log_auditoria    (+ vistas de resumen y cobertura)

── BI (schema ades_bi) ────────────────────────────────────────────────
mv_asistencia_diaria       mv_calificaciones_grupo  mv_riesgo_academico
mv_resumen_plantel         mv_cobertura_curricular

── Memoria IA (schema memoria) ────────────────────────────────────────
memoria.sesiones           memoria.embeddings       memoria.decisiones
```

### Particionamiento (migración `066_particionamiento_tablas.sql`)

Las tablas de mayor volumen están particionadas por `ciclo_escolar_id`:

| Tabla | Registros | Particiones |
|-------|-----------|-------------|
| `ades_asistencias` | 180,000+ | 2025-2028 |
| `ades_calificaciones_periodo` | 76,320 | 2025-2028 |

---

## BFF Spring Boot — Arquitectura Hexagonal

El BFF (Backend for Frontend) implementa el patrón de puertos y adaptadores (ADR-0008) para 57 módulos de dominio:

```
┌──────────────────────────────────────────────────────────────┐
│                  Spring Boot BFF :8080                        │
│                                                              │
│  HTTP Request                                                 │
│       │                                                       │
│  ┌────▼──────────────────┐                                    │
│  │   Controller Layer    │  ← slim, ≤5 deps, sin SQL          │
│  └────┬──────────────────┘                                    │
│       │ invoca puerto IN                                      │
│  ┌────▼──────────────────┐                                    │
│  │  Application Service  │  @Service — reglas de negocio      │
│  │  implements UseCase   │  (CrearAlumno, Actualizar…)        │
│  └────┬──────────────────┘                                    │
│       │ invoca puerto OUT                                     │
│  ┌────▼──────────────────┐                                    │
│  │  Persistence Adapter  │  JdbcTemplate + JPA → PostgreSQL   │
│  └───────────────────────┘  (via PgBouncer :6432)             │
└──────────────────────────────────────────────────────────────┘
```

**Módulos hexagonales completos (57/57):** alumnos, profesores, materias, planteles, certificados, aulas, boletas, geo, foros, catalogos, y 47 módulos adicionales de dominio.

**Observabilidad:** Micrometer → `/actuator/prometheus` → Grafana dashboard JVM (11 paneles: heap gauge, HTTP req/sec, latencia p50/p95/p99, HikariCP pool, GC pause).

**Secretos:** Spring Cloud Vault — `secret/ades` con DATABASE_URL, credenciales, API keys.

---

## Calidad de Software y Testing

### Suite E2E con Playwright (17 suites)

| Suite | Cobertura |
|-------|-----------|
| `01-auth.spec.ts` | Login OIDC, PKCE, tokens, guards |
| `02-alumnos.spec.ts` | CRUD alumnos, importación, búsqueda |
| `03-asistencias.spec.ts` | Registro, estados, alertas |
| `04-calificaciones.spec.ts` | Libreta editable, bulk save, rúbricas |
| `05-certificados.spec.ts` | Emisión, firma Ed25519, QR, descarga PDF |
| `06-chaos.spec.ts` | Resiliencia ante errores de red y timeout |
| `07-fuzz.spec.ts` | Inputs maliciosos, caracteres especiales, inyección |
| `08-api.spec.ts` | Contratos de API, schemas Pydantic |
| `09-concurrency.spec.ts` | Optimistic locking, condiciones de carrera |
| `10-rbac.spec.ts` | Elevation attacks, cross-plantel, fake JWT, route guards |
| `11-business-flows.spec.ts` | Conducta, reinscripción, movilidad, justificaciones, comunicados |
| `12-certificados.spec.ts` | Director access, RBAC coordinador, verificación pública, folio fuzzing |
| `13-rrhh.spec.ts` | Licencias, capacitaciones, expediente laboral, asistencia personal |
| `14-a11y.spec.ts` | WCAG 2.1 AA con AxeBuilder — hallazgos como `console.warn` (no bloqueante) |
| `15-audit-integrity.spec.ts` | `row_version` triggers, integridad del log de auditoría |
| `16-cierre-ciclo.spec.ts` | Promoción automática, REPROBADO/EGRESADO/BAJA |
| `17-advanced.spec.ts` | Double-submit, MIME spoofing, XSS persistido, Gremlins.js monkey testing |

Cada suite inyecta `ades_token` + `ades_usuario` en `sessionStorage` para evitar el flujo OIDC real en CI.

### Seguridad Validada

| Hallazgo | Estado |
|---------|--------|
| MIME type spoofing en uploads | Validación con `python-magic` (magic bytes reales) |
| Optimistic locking en PATCH | `check_row_version()` conectado en `/usuarios` y `/alumnos` |
| XSS persistido | Angular sanitización verificada en chatbot y buscador |
| Audit trail sin usuario_id | `get_ades_user` propaga a `request.state` en 100% endpoints mutantes |
| DELETE sobre log_auditoria | `ades_app` sin permiso DELETE — solo `ades_admin` |
| Triggers de auditoría duplicados | Migración `079` eliminó duplicados (0 duplicados verificado) |

---

## Telemetría y Observabilidad

### Grafana (5 dashboards)

| Dashboard | Métricas clave |
|-----------|---------------|
| **ADES API** | Latencia p50/p95/p99, requests/seg, errores 4xx/5xx |
| **Spring BFF JVM** | Heap gauge, memoria, threads, HikariCP pool, GC pause |
| **PostgreSQL** | Cache hit ratio (98.89%), locks, queries lentas, conexiones |
| **PgBouncer** | Pool activo/idle, transacciones/seg, tiempo de espera |
| **Alertas** | 13 alert rules Prometheus — latencia, uptime, memoria, disco |

### Prometheus Scraping

```
ades-api     :8000/metrics  →  FastAPI (Starlette Prometheus)
ades-bff     :8080/actuator/prometheus  →  Spring Boot Micrometer
postgres_exporter  :9187  →  5,700+ métricas PostgreSQL
pgbouncer_exporter :9127  →  métricas PgBouncer
```

### Monitoreo de Celery

Flower en `:5555` — cola de tareas en tiempo real, workers activos, tareas fallidas, tiempo de ejecución. Tareas programadas (Celery Beat):

- **Nocturno:** `REFRESH MATERIALIZED VIEW CONCURRENTLY` de las 5 MVs en `ades_bi`
- **Periódico:** scan de alertas académicas, envío de comunicados recurrentes
- **On-demand:** OCR de documentos nuevos en Paperless-ngx, batch de boletas ZIP

---

## Reglas de Negocio Críticas

1. **Primaria:** 1 titular por grupo cubre todas las materias. Sin límite de docentes de la misma materia por plantel.
2. **Secundaria / Preparatoria:** uno o más profesores por materia por plantel, asignados individualmente a cada grupo.
3. **Grupos:** siempre A y B. Máximo 2 por grado salvo instrucción explícita.
4. **Ixtapan:** Secundaria completa (3 grados activos). Preparatoria proyectada con `is_active=FALSE`.
5. **Tenancingo prep:** Semestres 1-2 activos (ciclo 26B); sem 3-6 `is_active=FALSE`.
6. **Metepec prep:** Semestres 1-4 activos (26B/27A); sem 5-6 `is_active=FALSE`.
7. **Calendario SEP** para Primaria y Secundaria; **calendario UAEMEX** solo para Preparatoria.
8. **Escalas:** SEP 0-10 mínimo 6.0 / UAEMEX 0-100 mínimo 60.0 — dinámicas por `ades_niveles_educativos.minimo_aprobatorio`.
9. **Optimistic locking:** PATCH sobre recursos con `row_version` retorna 409 si está desactualizado.
10. **Certificados:** folio único verificable vía QR. Firma Ed25519 con llave privada en Vault (nunca en BD).
11. **Cierre de ciclo:** `cerrar_ciclo_y_promover()` — BAJA no se reinscribe, REPROBADO repite grado, EGRESADO no se reinscribe, sin grupo destino → `ades_promociones_pendientes`.
12. **Fechas en UI:** DD-MM-YYYY. Idioma: español.

---

## Subdominios y TLS

| Dominio | Servicio | Puerto |
|---------|---------|--------|
| `ades.setag.mx` | Frontend Angular + API ADES | 443 → 4200 / 8080 |
| `auth.ades.setag.mx` | Authentik IdP | 443 → 9010 |
| `bi.ades.setag.mx` | Apache Superset | 443 → 8088 |
| `monitor.ades.setag.mx` | Grafana | 443 → 3003 |
| `notify.ades.setag.mx` | ntfy push SSE | 443 → 2586 |

Certificados Let's Encrypt renovados automáticamente vía `certbot` del sistema (bind mount `/etc/letsencrypt:/etc/letsencrypt:ro` en nginx).

---

## Estructura del Repositorio

```
/opt/ades/
├── .agent/                          # Contexto cognitivo del agente
│   ├── CONTEXT.md                   # Especificación completa
│   ├── STATE.md                     # Bitácora de sesiones
│   └── MAP.md                       # Mapa de módulos y rutas API
│
├── DECISIONS/                       # Architecture Decision Records
│   ├── 0001-genesis-architecture.md
│   ├── 0002-uuid-primary-keys.md
│   ├── 0003-apex-component-library.md
│   ├── 0004-firma-digital-ed25519.md
│   ├── 0005-audit-trail-propagacion-usuario.md
│   ├── 0006-rbac-scope-plantel.md
│   ├── 0007-jwks-async-ttl-cache.md
│   ├── 0008-hexagonal-solid-migration.md
│   ├── 0009-vault-pgbouncer-secrets.md
│   └── 0010-hexagonal-completar-modulos-flat.md
│
├── db/
│   ├── migrations/                  # 84 scripts DDL/DML numerados (001–082)
│   ├── seeds/                       # Datos 2026-2027 (3,483 usuarios, 76,320 calificaciones)
│   └── scripts/
│       ├── reset_and_reseed.sh      # Drop + recrear + seeds (solo desarrollo)
│       └── backup_postgres.sh       # Backup con rotación 30 días
│
├── backend/                         # FastAPI + Celery
│   ├── app/
│   │   ├── api/v1/                  # Routers: IA, OCR, PDF, certs, H5P, BBB, push, webhooks
│   │   ├── models/                  # SQLAlchemy ORM async
│   │   ├── schemas/                 # Pydantic v2 DTOs
│   │   ├── services/                # llm_service, firma_digital, semantic_cache
│   │   ├── core/                    # security, config, optimistic_locking, audit middleware
│   │   └── worker/                  # Celery tasks: OCR, notificaciones, boletas, BI refresh
│   └── Dockerfile                   # python:3.12-slim-bookworm
│
├── bff/                             # Spring Boot BFF hexagonal
│   └── src/main/java/mx/nevadi/ades/
│       ├── domain/port/in/          # Use cases (Commands)
│       ├── domain/port/out/         # Repository ports
│       ├── application/service/     # ApplicationService @Service
│       ├── infrastructure/
│       │   ├── inbound/web/         # Controllers (slim, ≤5 deps)
│       │   └── outbound/persistence/ # JdbcTemplate + JPA adapters
│       └── HexagonalConfig.java     # Bean wiring
│
├── frontend/                        # Angular 22 + PrimeNG
│   ├── src/app/
│   │   ├── core/                    # AuthService, ApiService, ContextService
│   │   ├── shared/                  # InteractiveGridComponent, ApexNotificationService
│   │   └── features/                # 55+ módulos lazy-loaded
│   └── e2e/
│       └── tests/                   # 17 suites Playwright
│
├── portal/                          # Portal externo de admisión (Angular :4201)
│
├── infrastructure/
│   ├── nginx/nginx.conf             # Reverse proxy 6 dominios + TLS
│   ├── authentik/setup.py           # Setup OIDC automatizado
│   ├── superset/                    # create_dashboards.py + init.sh
│   ├── grafana/                     # Dashboards JSON provisioned
│   ├── vault/                       # Políticas y configuración inicial
│   ├── pgbouncer/                   # pgbouncer.ini (SCRAM-SHA-256)
│   └── h5p/                         # Dockerfile Node.js + @lumieducation/h5p-server
│
├── memory/                          # IA residente
│   ├── semantic_cache.py            # Valkey + fastembed ONNX (ARM64)
│   └── long_term_memory.py          # PostgreSQL + pgvector HNSW
│
├── docs/                            # Documentación técnica
│   ├── disaster_recovery_plan.md    # DRP: RPO 24h, RTO 2h
│   └── manual_usuario_ades.md       # Manual completo por módulo
│
├── docker-compose.yml               # Stack completo (28 servicios)
├── .env.example                     # Template de variables
└── .env                             # Valores reales (gitignored)
```

---

## Instalación

### Requisitos

- Docker 29+ con Compose v2
- Ubuntu 24 LTS (ARM64 recomendado — OCI Always Free)
- RAM: 24 GB mínimo
- Almacenamiento: 100 GB en `/opt/ades`
- Dominio con DNS configurado al servidor

### Despliegue

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
sudo certbot certonly --standalone \
  -d ades.setag.mx -d auth.ades.setag.mx -d bi.ades.setag.mx \
  -d monitor.ades.setag.mx -d notify.ades.setag.mx \
  --email admin@setag.mx --agree-tos --non-interactive

# 5. Infraestructura base
docker compose up -d postgres valkey seaweedfs vault
sleep 20

# 6. Inicializar BD (schema UUID v7 + seeds 2026-2027)
bash db/scripts/reset_and_reseed.sh

# 7. Stack completo
docker compose up -d

# 8. Configurar Authentik (OIDC apps, MFA, grupos)
python3 infrastructure/authentik/setup.py

# 9. Superset — dashboards BI
python3 infrastructure/superset/create_dashboards.py

# 10. Grafana — dashboards telemetría (auto-provisioned desde infrastructure/grafana/)
```

### Variables de Entorno Críticas

```bash
# Base de datos
POSTGRES_DB=ades
POSTGRES_USER=ades_admin
POSTGRES_PASSWORD=<seguro>

# Caché
VALKEY_PASSWORD=<seguro>

# Autenticación
AUTHENTIK_SECRET_KEY=<openssl rand -hex 32>
AUTHENTIK_BOOTSTRAP_PASSWORD=<seguro>
AUTHENTIK_BOOTSTRAP_TOKEN=<openssl rand -hex 32>
OIDC_ISSUER=https://ades.setag.mx/auth/application/o/ades/
OIDC_CLIENT_ID=ades-frontend
OIDC_CLIENT_SECRET=<desde Authentik UI>

# Secretos
VAULT_TOKEN=<desde vault init>

# IA
OPENAI_API_KEY=<clave NVIDIA NIM>
OPENAI_BASE_URL=https://integrate.api.nvidia.com/v1

# Firma digital
FIRMA_CLAVE_PRIVADA_HEX=<generada con firma_digital.generar_nuevo_par_de_llaves()>

# BigBlueButton (opcional — requiere servidor BBB propio)
BBB_SERVER_URL=
BBB_SHARED_SECRET=

# Superset dashboards (UUIDs generados al crear dashboards)
SUPERSET_DASHBOARD_INSTITUTO_UUID=80e35fc4-...
SUPERSET_DASHBOARD_PLANTEL_UUID=e3cf59d7-...
```

---

## Monitoreo y Mantenimiento

### Health Checks

```bash
# Estado general
docker compose ps

# Servicios individuales
docker compose exec postgres pg_isready -U ades_admin
docker compose exec valkey valkey-cli -a $VALKEY_PASSWORD ping
curl -s https://ades.setag.mx/api/v1/health
curl -s https://auth.ades.setag.mx/-/health/live/

# Cobertura de auditoría
docker compose exec postgres psql -U ades_admin -d ades \
  -c "SELECT * FROM auditoria.reporte_cobertura() LIMIT 20;"
```

### Backup

```bash
# Manual
docker compose exec -T postgres pg_dump -U ades_admin ades \
  | gzip > backup_ades_$(date +%F_%H%M).sql.gz

# Automático (configurar en cron del sistema)
bash /opt/ades/scripts/backup_postgres.sh
bash /opt/ades/scripts/backup_minio.sh  # ahora usa SeaweedFS
```

Plan de Recuperación ante Desastres documentado en `docs/disaster_recovery_plan.md` — RPO: 24 horas, RTO: 2 horas.

### Solución de Problemas Comunes

| Síntoma | Causa | Solución |
|---------|-------|----------|
| Nginx: `cannot load certificate` | Cert en host, no en volumen Docker | Bind mount `/etc/letsencrypt:/etc/letsencrypt:ro` |
| PgBouncer: autenticación falla con PG18 | `AUTH_TYPE: md5` incompatible | Usar `AUTH_TYPE: scram-sha-256` |
| BFF: `column ne.clave_nivel does not exist` | `ades_niveles_educativos` tiene `nombre_nivel` | Fix en `PlantelQueryService.java` |
| FastAPI: UUID params sin cast en JdbcTemplate | `?` sin tipo en queries | Usar `?::uuid`, `?::text`, `?::boolean` |
| MVs `ades_bi` vacías | `REFRESH CONCURRENTLY` falla en MV vacía | Hacer primer `REFRESH` sin CONCURRENT |
| Celery tasks: `AttributeError psycopg2` | `psycopg2-binary` faltante | Agregar a `requirements.txt` |
| Token no encontrado en Angular | Clave `ades_access_token` incorrecta | Usar siempre `inject(AuthService).accessToken()` |

---

## Decisiones de Arquitectura (ADRs)

| ADR | Título | Razón principal |
|-----|--------|-----------------|
| 0001 | Arquitectura de Génesis | FastAPI + Angular + PostgreSQL para operación completa |
| 0002 | UUID v7 como Primary Keys | Time-ordered sin fragmentación de índice B-tree |
| 0003 | APEX Component Library | `ApexNotificationService` + `InteractiveGridComponent` globales |
| 0004 | Firma Digital Ed25519 | Costo $0, verificable sin PKI, compatible con Polygon PoS |
| 0005 | Audit Trail via `request.state` | `get_ades_user` propaga UUID real — sin JWT decode duplicado |
| 0006 | RBAC con Scope de Plantel | Verificación `plantel_id` en endpoints mutantes críticos |
| 0007 | JWKS Async + TTL Cache | Evita bloqueo de event loop; caché 5 min resiste key rotation |
| 0008 | Hexagonal SOLID en BFF | Controladores slim, ApplicationService con Use Cases, Ports & Adapters |
| 0009 | Vault + PgBouncer Secrets | Secretos rotativos, no texto plano en `.env` en producción |
| 0010 | Completar hexagonal módulos flat | `@Service` vs `@Bean` para ApplicationService con múltiples interfaces |

---

## Comparativa con Alternativas

| Área | Alternativa | ADES |
|------|-------------|------|
| **Tipo** | LMS (Moodle) | SIS + LMS integrado |
| **Multi-plantel** | Single-tenant | 3 planteles, 2 autoridades (SEP/UAEMEX) |
| **Horarios** | Básico | aSc TimeTables (motor K-12 especializado) |
| **Regulación** | Genérica | SEP/UAEMEX específica México |
| **Stack** | PHP/MySQL | FastAPI/Spring Boot/PostgreSQL 18 |
| **Soberanía** | Cloud SaaS | 100% self-hosted, git-ops |
| **IA** | Módulos básicos | Claude API + NVIDIA NIM + LangChain |
| **Auth** | Ldap/SSO básico | Authentik OIDC + MFA + Google Workspace |
| **Certificación** | Manual | Ed25519 + QR + portal público de verificación |

---

## Licencia

**Privado — Instituto Nevadi © 2026**

Sistema propietario del Instituto Nevadi. Prohibida la distribución, modificación o uso sin autorización explícita del titular.

Desarrollado con **Claude Code** (Anthropic).

---

## Contacto

| Plantel | Email | Teléfono |
|---------|-------|----------|
| **Metepec** | nevadimetepec@institutonevadi.edu.mx | 7222971441 · 7223253683 |
| **Tenancingo** | nevaditenancingo@institutonevadi.edu.mx | 7141424323 |
| **Ixtapan de la Sal** | nevadiixtapan@institutonevadi.edu.mx | 7211433015 |

**Repositorio:** https://github.com/imarthe75/ades-nevadi  
**Última actualización:** Junio 2026 · v3.0

---

## Próximas Acciones — Tareas Administrativas

**IMPORTANTE:** Todas las instrucciones detalladas están en **`TAREAS_ADMINISTRATIVAS.md`**

### Resumen de 5 Tareas Finales

| # | Tarea | Tiempo | Criticidad |
|---|-------|--------|-----------|
| 1 | Migrar `POSTGRES_USER` ades_admin → ades_app | 15 min | Media |
| 2 | Asignar usuarios a grupo ADES Admins en Authentik | 10 min | Alta |
| 3 | Resolver tests E2E BIZ-04 + CER-E2E-10 | 20 min | Media |
| 4 | BigBlueButton (Decisión: mantener deshabilitado) | — | Baja |
| 5 | H5P contenido educativo (Decisión: instalar) | 10 min | Media |

### Decisiones sobre BBB y H5P

**BigBlueButton (BBB):**
- ❌ No es imagen Docker — requiere servidor externo
- ❌ Tiene costo (~$0.20/usuario/mes con Blindside)
- ✅ **Decisión: Mantener en ADES pero deshabilitado** → Endpoints retornan 503 "No configurado"
- Si Nevadi adquiere BBB en futuro: solo actualizar `.env` (BBB_SERVER_URL, BBB_SHARED_SECRET)

**H5P (Quizzes, videos interactivos):**
- ✅ Imagen Docker Node.js lista (ades-h5p)
- ✅ Software libre — sin costo
- ✅ **Decisión: Instalar H5P** → 10 min (descargar h5p-core desde h5p.org)
- Archivos en `/opt/ades/data/h5p-core/` (volumen Docker)

### Estado Listo para Pruebas

Una vez completadas estas 5 tareas:

✅ **Sistema completamente funcional y listo para plan de pruebas integral**

Documentación: Ver **`TAREAS_ADMINISTRATIVAS.md`** (instrucciones paso a paso, comandos, validaciones)

