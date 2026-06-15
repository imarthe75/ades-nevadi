# 🗺️ ADES Nevadi — Master Technical Roadmap (Phases 1-35)

Este documento unifica, limpia y secuencia de forma definitiva el plan de desarrollo del sistema ADES (Administración Escolar Instituto Nevadi), resolviendo duplicidades numéricas, inconsistencias de nomenclatura y mapeando el estado de avance actual.

---

## 🏛️ Bloque A: Core Académico y Operativo (Fases 1-10)
*Estado: ✅ 100% COMPLETADO*

*   **FASE 1: Datos Maestros**: Estructuras de Planteles, Grados, Ciclos, Grupos, Alumnos y Profesores.
*   **FASE 2: Control Académico**: Calificaciones por período (SEP/UAEMEX), Registro de Asistencias y Tareas Escolares.
*   **FASE 3: Operación Escolar**: Gestión de Horarios, Registro de Conducta y Ficha de Expediente Médico Básico.
*   **FASE 4: Inteligencia Artificial Base**: Chatbot pedagógico conversacional y traductor de Lenguaje Natural a Consultas SQL (NL to SQL) utilizando NVIDIA NIM local.
*   **FASES 5-10: Extensiones de Operación**: Menús dinámicos, catálogos institucionales base, notificaciones push (ntfy) y tabuladores.

---

## 🔌 Bloque B: Rejillas Interactivas y Planes de Estudio (Fases 24-26)
*Estado: ✅ 100% COMPLETADO*

*   **FASE 24: Rejillas APEX (Interactive Grids)**: Reemplazo de tablas CRUD por rejillas interactivas con edición inline, ordenamiento, filtrado por cabecera y exportación CSV (Alumnos, Profesores y Grupos).
*   **FASE 25: Control de Concurrencia (Optimistic Locking)**: Mecanismo de bloqueo optimista mediante `row_version` en base de datos y validaciones HTTP 409 Conflict.
*   **FASE 26: Planes de Estudio Completos (NEM & CBU)**: Carga de 648 temas específicos para Primaria (Plan NEM 2022) y Preparatoria (CBU 2024).

---

## 🛡️ Bloque C: Seguridad Criptográfica, Gestión de Personal y Analítica (Fases 27-32)
*Estado: ✅ 100% COMPLETADO*

*   **FASE 27: Firmas Digitales y Certificados**: Criptografía Ed25519 para generación de certificados de estudios en PDF con código QR y verificación pública (sin auth).
*   **FASE 28: Expediente Digital Documental**: Carga de documentos de ingreso a MinIO S3, análisis OCR con Paperless-ngx e inspección inteligente NIM.
*   **FASE 29: Gestión de Licencias y Capacitaciones (RRHH)**: Solicitudes y flujos de aprobación de licencias, y acumulación de horas de capacitación del personal docente.
*   **FASE 30: RRHH Avanzado (Expediente Laboral)**: Expediente laboral cifrado simétricamente (Fernet) en Python, disponibilidad horaria del personal y registro de asistencia diaria.
*   **FASE 31: Justificaciones y Alertas Médicas**: Módulo de justificaciones aprobado por Directivos para recálculo automático de inasistencias y alertas visuales de condiciones crónicas.
*   **FASE 32: IA Analítica, Salud Avanzada y Foros**:
    - *IA*: Predicción de abandono escolar, learning paths adaptativos e identificación semántica de bullying en encuestas.
    - *Salud*: Dispensación de medicamentos, actas de incidentes en PDF y certificados de aptitud deportiva (WeasyPrint).
    - *Colaboración*: Foros de discusión por materia/tutoría y widgets personalizables en dashboard (localStorage).

---

## 🚀 Bloque D: Consolidación, Gaps e Integraciones (Fases 33-35)
*Estado: ⏳ PENDIENTES / ROADMAP FUTURO*

*   **FASE 33: Consolidación de Infraestructura y HA (Gaps Identificados)**:
    - **HashiCorp Vault**: Automatizar el unseal (desellado) y la inyección dinámica del token de secretos en `ades-api` (quitando texto plano de `.env`).
    - **Apache Superset**: Automatizar la inicialización del contenedor inyectando la conexión a la base de datos `ades` y la creación del usuario administrador.
    - **Grafana**: Pre-aprovisionar dashboards de telemetría a través de plantillas JSON.
    - **ntfy**: Habilitar volumen de persistencia para la base de datos SQLite.
    - **Celery Flower**: Levantar interfaz web para telemetría de tareas asíncronas en segundo plano.
*   **FASE 34: Integraciones SEP y Documentación ZIP**:
    - Importación de preinscripciones desde portal SEP.
    - Generación y descarga de expedientes completos de alumnos consolidados en archivos ZIP (Stirling-PDF).
*   **FASE 35: Cierre de Ciclo Escolar e Indicadores de Uso**:
    - Actas de inicio y cierre de ciclo en PDF.
    - Monitoreo en Prometheus de almacenamiento en disco e informes finales del ciclo escolar.
