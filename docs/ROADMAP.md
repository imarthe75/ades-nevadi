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
*Estado: ✅ COMPLETADO (2026-07-03) — salvo 1 ítem explícitamente deprioritizado*

*   **FASE 33: Consolidación de Infraestructura y HA (Gaps Identificados)**:
    - ⏸️ **HashiCorp Vault**: unseal automático + AppRole auth **deprioritizado** — el token sigue leyéndose de `root_token.txt` como fallback en `backend/app/core/vault.py`. Es una mejora de endurecimiento (no bloqueante); se deja fuera de alcance por el riesgo de romper el arranque si se hace mal, hasta que se solicite explícitamente.
    - ✅ **Apache Superset**: `integrations/superset/docker-init.sh` ya automatiza la conexión a `ades` y la creación del admin; **2026-07-03** se agregó además la llamada a `create_dashboards.py` (idempotente) en el mismo arranque, para que un volumen recreado desde cero quede con los dashboards ya aprovisionados sin pasos manuales.
    - ✅ **Grafana**: 7 dashboards JSON pre-aprovisionados y montados vía provisioning.
    - ✅ **ntfy**: volúmenes `ntfy-data`/`ntfy-cache` declarados y persistiendo la base SQLite.
    - ✅ **Celery Flower**: interfaz web activa en `/flower/` (proxied por nginx); **2026-07-03** se agregó healthcheck al servicio en `docker-compose.yml`.
*   **FASE 34: Integraciones SEP y Documentación ZIP** — ✅ completado 2026-07-03:
    - ✅ Importación de preinscripciones desde portal SEP: conectada al frontend (`admision.component.ts` usaba una clave de entidad inexistente `"admision"`, corregida a `"preinscritos-sep"` — bug real que causaba 404 en el botón de plantilla/importación).
    - ✅ Generación y descarga de expedientes completos de alumnos consolidados en archivos ZIP, ahora con compresión real vía Stirling-PDF (`common/ZipService.java` invoca el proxy FastAPI `/api/v1/pdf/comprimir` antes de empaquetar cada PDF, con fallback transparente al original si Stirling falla).
*   **FASE 35: Cierre de Ciclo Escolar e Indicadores de Uso** — ✅ completado 2026-07-03:
    - ✅ Actas de inicio y cierre de ciclo en PDF: ya implementadas end-to-end (`CierreCicloController.java` + `cierre-ciclo.component.ts`, proxy a FastAPI).
    - ✅ Monitoreo en Prometheus de almacenamiento en disco: servicio `node-exporter` + scrape config + reglas de alerta (`DiskSpaceLow`/`DiskSpaceCritical`) + panel en el dashboard `infrastructure_overview.json` de Grafana.
    - Informes finales de ciclo escolar: cubiertos por los indicadores + actas de cierre de ciclo ya mencionados — no se identificó trabajo adicional de infraestructura pendiente en este rubro.
