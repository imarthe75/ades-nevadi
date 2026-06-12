# ADES Nevadi — Plan de Implementación y Tareas
## Roadmap Técnico: Fases 27-35 (H2 2026 — H2 2027)

**Versión:** 2.0  
**Fecha:** Junio 2026  
**Responsable:** Equipo de Arquitectura + Claude Code  
**Última actualización:** 2026-06-11

---

## 🎯 Propósito del Documento

Este plan define el **desglose técnico completo** para implementar los 195 casos de uso ADES, organizados en **9 fases de desarrollo** (27-35), cada una con:

- 📋 Tareas técnicas específicas (DDL, modelos, endpoints, componentes)
- ⏱️ Estimaciones de esfuerzo en horas/días
- 🔗 Dependencias y precedencias
- ✅ Checklist de validación
- 📦 Artifacts entregables

---

## Leyenda de Estados

| Símbolo | Significado |
|---------|-----------|
| ✅ | Completado y desplegado en producción |
| 🔧 | En progreso o pendiente en sesión actual |
| ⏳ | Programado para fase siguiente |
| ⚠️ | Bloqueado por dependencia externa |
| 🚫 | Descartado o postergado a futuro |

---

## FASE 27: Consolidación de Base & Recuperación (Jul-Ago 2026)

**Objetivo:** Asegurar estabilidad operativa, seguridad de datos y automatización básica.  
**Duración:** 40 horas  
**Prioridad:** 🔴 CRÍTICA

### 27.1 Backup Automático y Recuperación ante Desastre

#### TAREA 27.1.1: Script de Backup PostgreSQL
- **Descripción:** Crear script bash con `pg_dump` parametrizado, compresión gzip, y rotación de 30 días.
- **Artifacts:**
  - `scripts/backup_postgres.sh` (bash)
  - `scripts/backup_config.env`
  - `docker-compose.yml` actualizado con volumen de backups
- **Estimado:** 4 horas
- **Checklist:**
  - [x] Script ejecutable, sin errores de sintaxis
  - [x] Probado con restore en BD de prueba
  - [x] Compresión verificada (antes/después tamaño)
  - [x] Rotación de archivos correcta (30 últimos días)
  - [x] Cron job configurado en host

#### TAREA 27.1.2: Backup de MinIO (archivos)
- **Descripción:** Script para sincronizar buckets MinIO → almacenamiento externo (S3 compatible o local).
- **Artifacts:**
  - `scripts/backup_minio.sh`
  - Documentación de DR plan
- **Estimado:** 3 horas
- **Checklist:**
  - [x] Sincronización bidireccional probada
  - [x] Verificación de integridad (checksums)
  - [x] Plan de recuperación documentado

#### TAREA 27.1.3: Documentación de Disaster Recovery
- **Descripción:** Guía de pasos para restaurar BD + archivos desde backup.
- **Artifacts:**
  - `docs/disaster_recovery_plan.md`
  - `docs/restauracion_paso_a_paso.md`
  - **Estimado:** 3 horas
  - **Checklist:**
  - [x] Plan probado en ambiente de staging
  - [x] Métricas RTO/RPO documentadas
  - [x] Responsables identificados

---

### 27.2 Reinscripción Masiva (Ciclo Escolar)

#### TAREA 27.2.1: DDL para Reinscripción
- **Descripción:** Migración `031_reinscripcion.sql` con tablas y funciones auxiliares.
- **Contenido:**
  ```sql
  -- Tabla de validación de reinscripción
  CREATE TABLE ades_reinscripcion_ciclo (
      id UUID PRIMARY KEY DEFAULT uuidv7(),
      ciclo_escolar_id UUID NOT NULL REFERENCES ades_ciclos_escolares(id),
      alumno_id UUID NOT NULL REFERENCES ades_estudiantes(id),
      estado VARCHAR(20), -- PENDIENTE, VALIDADO, APROBADO, RECHAZADO
      razon_rechazo TEXT,
      adeudos_pendientes BOOLEAN,
      validated_at TIMESTAMP,
      created_at TIMESTAMP DEFAULT NOW()
  );

  -- Función para validar adeudos (cobranza, uniformes)
  CREATE OR REPLACE FUNCTION validar_adeudos_alumno(
      alumno_id UUID, 
      ciclo_id UUID
  ) RETURNS TABLE (adeuda BOOLEAN, detalle JSONB) AS $$
  BEGIN
      -- Lógica de integración con módulo de cobranza
      RETURN QUERY SELECT 
          COALESCE(COUNT(*) > 0, FALSE),
          jsonb_agg(jsonb_build_object('tipo', 'cuota', 'monto', 0))
      FROM ades_adeudos 
      WHERE estudiante_id = alumno_id;
  END;
  $$ LANGUAGE plpgsql;
  ```
- **Estimado:** 6 horas
- **Checklist:**
  - [x] Migración ejecutada sin errores
  - [x] Función probada con datos reales
  - [x] Índices creados para performance
  - [x] Trigger de auditoría en tabla nueva

#### TAREA 27.2.2: Backend: Endpoints de Reinscripción
- **Descripción:** FastAPI router `reinscripcion.py` con endpoints masivos.
- **Endpoints:**
  - `POST /reinscripcion/{ciclo_id}/validar` — valida todos los alumnos
  - `POST /reinscripcion/{ciclo_id}/aprobar-masivo` — aprueba reinscripción
  - `GET /reinscripcion/{ciclo_id}/reporte` — estado de cada alumno
  - `POST /reinscripcion/{ciclo_id}/generar-cartas` — PDF de aceptación
- **Estimado:** 8 horas
- **Checklist:**
  - [x] Endpoints implementados y documentados en OpenAPI
  - [x] Validaciones de permisos (solo admin/plantel)
  - [x] Errores 400/500 manejados correctamente
  - [x] Logs de auditoría en cada operación
  - [x] Tests unitarios (pytest) ≥ 80% coverage

#### TAREA 27.2.3: Frontend: Componente Reinscripción
- **Descripción:** Angular component para visualizar estado de reinscripción masiva.
- **Estructura:**
  - Tabla resumen (alumno, estado, adeudos, acción)
  - Filtros (estado, plantel, grado)
  - Botones de acción (validar, aprobar, ver carta)
  - Progress bar de proceso
- **Estimado:** 6 horas
- **Checklist:**
  - [x] Component compilado sin errores
  - [x] Tabla pagination server-side (500+ alumnos)
  - [x] Dialogs para confirmación de acciones
  - [x] Export CSV de reporte
  - [x] Responsive en móvil

---

### 27.3 Cierre de Período Académico

#### TAREA 27.3.1: DDL y Funciones de Cierre
- **Descripción:** Migración `032_cierre_periodo.sql` con lógica de validación y bloqueo.
- **Contenido:**
  ```sql
  ALTER TABLE ades_calificaciones_periodo 
  ADD COLUMN cerrada BOOLEAN DEFAULT FALSE,
  ADD COLUMN fecha_cierre TIMESTAMP,
  ADD COLUMN cerrado_por UUID;

  CREATE TABLE ades_cierre_periodo_log (
      id UUID PRIMARY KEY DEFAULT uuidv7(),
      periodo_id UUID REFERENCES ades_periodos_evaluacion(id),
      ciclo_id UUID,
      grupo_id UUID,
      calificaciones_validadas INT,
      alumnos_sin_calificacion INT,
      estado VARCHAR(20), -- ABIERTO, EN_CIERRE, CERRADO
      cerrado_por UUID,
      fecha_cierre TIMESTAMP
  );

  -- Función que valida antes de cerrar
  CREATE OR REPLACE FUNCTION validar_cierre_periodo(
      periodo_id UUID
  ) RETURNS TABLE (
      puede_cerrar BOOLEAN,
      alumnos_faltantes INT,
      detalles JSONB
  ) AS $$
  BEGIN
      -- Verifica que todos los alumnos tengan calificación
      -- Genera reporte de inconsistencias
  END;
  $$ LANGUAGE plpgsql;
  ```
- **Estimado:** 5 horas
- **Checklist:**
  - [ ] Migración ejecutada
  - [ ] Función de validación probada
  - [ ] Backup automático al cerrar período
  - [ ] Trigger de auditoría

#### TAREA 27.3.2: Backend: Endpoint de Cierre
- **Descripción:** FastAPI endpoint que ejecuta cierre con validaciones.
- **Lógica:**
  1. Validar que todas las calificaciones estén ingresadas
  2. Generar snapshot histórico (tabla `ades_calificaciones_historico`)
  3. Bloquear edición (set `cerrada = TRUE`)
  4. Crear backup automático de BD
  5. Emitir notificación a docentes
- **Estimado:** 6 horas
- **Checklist:**
  - [ ] Endpoint `POST /periodos/{id}/cerrar` implementado
  - [ ] Manejo de transacciones (rollback si hay error)
  - [ ] Email de confirmación a coordinador
  - [ ] Auditoría completa

#### TAREA 27.3.3: Frontend: Asistente de Cierre
- **Descripción:** Wizard en 4 pasos: seleccionar período → validar → revisar → confirmar.
- **Estimado:** 5 horas
- **Checklist:**
  - [ ] Component con routing de pasos
  - [ ] Tabla de alumnos sin calificación (step 2)
  - [ ] Resumen de cambios (step 3)
  - [ ] Botón de confirmación con doble verificación

---

### 27.4 Configuración de Email y Notificaciones

#### TAREA 27.4.1: Setup de SMTP
- **Descripción:** Integración de servidor de correo (Gmail, SendGrid o local).
- **Artifacts:**
  - Actualización `.env` con credenciales SMTP
  - Script de prueba `test_smtp.py`
- **Estimado:** 2 horas
- **Checklist:**
  - [ ] Email de prueba enviado exitosamente
  - [ ] Credenciales almacenadas en Vault (no en código)
  - [ ] Rate limiting configurado (no spam)

#### TAREA 27.4.2: Templates de Email
- **Descripción:** Jinja2 templates para notificaciones clave.
- **Templates:**
  - `email_reinscripcion_aprobada.jinja2`
  - `email_calificacion_disponible.jinja2`
  - `email_alerta_academica.jinja2`
  - `email_comunicado.jinja2`
- **Estimado:** 3 horas
- **Checklist:**
  - [ ] Todos con logo institucional
  - [ ] Personalizados (nombre, datos alumno)
  - [ ] Multiidioma (español/inglés si aplica)
  - [ ] Probados en Mailhog (staging)

---

### 27.5 Gestión de Aulas y Espacios ✅

#### TAREA 27.5.1: DDL de Aulas ✅
- **Descripción:** Migración `033_aulas_espacios.sql`.
- **Tablas:**
  - `ades_aulas` (nombre, plantel, piso, capacidad, recursos: proyector, pizarra, laboratorio)
  - `ades_disponibilidad_aula` (aula_id, horario, disponible)
- **Estimado:** 3 horas

#### TAREA 27.5.2: Backend: CRUD Aulas ✅
- **Descripción:** Endpoints para administración de aulas.
- **Estimado:** 4 horas

#### TAREA 27.5.3: Frontend: Gestor de Aulas ✅
- **Descripción:** Component grid con filtros por plantel/capacidad.
- **Estimado:** 3 horas

---

## FASE 28: Documentos y Expediente Digital ✅ COMPLETA (2026-06-11)

**Objetivo:** Gestión integral de documentos escolares con OCR y búsqueda.  
**Duración real:** ~40 horas  
**Estado:** ✅ COMPLETADA

### 28.1 Integración de Paperless-ngx ✅
- `ades-paperless` corriendo (ghcr.io/paperless-ngx/paperless-ngx:latest)
- Valkey como broker (DB=1 con auth), PostgreSQL como BD (`paperless` DB)
- OCR en español (tesseract-spa) activo
- API token generado y en Vault / `.env`
- `backend/app/services/paperless.py` — cliente async completo

### 28.2 Expediente Digital Consolidado ✅
- Migración `037_expediente_digital.sql` aplicada
  - `ades_expedientes_alumno` (alumno × ciclo, estado, completitud %)
  - `ades_expediente_documentos` (paperless_doc_id, tipo, estado_ocr, metadatos_ia)
  - `fn_calcular_completitud_expediente()` + trigger
- Backend: `api/v1/expediente_documentos.py` — 7 endpoints (GET/POST/DELETE/preview/verificar/analizar-ia)
- Frontend: `features/expediente-doc/expediente-doc.component.ts` — visor PDF + upload + análisis IA

### 28.3 Auditoría v2 ✅ (bonus)
- Migración `038_auditoria_v2.sql`: schema, log_auditoria, fn_auditoria_biu, fn_auditoria_aiud
- Migración `039_auditoria_biu_masivo.sql`: audit_biu en 90/90 tablas ades_*
- `asignar_biu()` → desarrollo; `asignar_triggers()` → producción
- `reporte_cobertura()` disponible para auditar estado en cualquier momento

**CUs completados:** AC-006, PE-002, PE-024, PE-025, AD-015, AD-016, AD-027

---

## FASE 29: Seguridad Avanzada y Recursos Humanos (2026-06-11 en progreso)

**Objetivo:** Vault integration, MFA, gestión de personal RRHH.  
**Prioridad:** 🔴 CRÍTICA

### 29.1 HashiCorp Vault (AD-024)

#### TAREA 29.1.1: Inicialización de Vault ✅
- Vault container corriendo (`ades-vault`)
- `backend/app/core/vault.py` — cliente hvac con fallback a env vars
- Vault inicializado con PostgreSQL storage backend
- Secretos sembrados: PAPERLESS_API_TOKEN, ANTHROPIC_API_KEY, claves DB

#### TAREA 29.1.2: Integración FastAPI → Vault
- Config arranca leyendo de Vault (con fallback a `.env`)
- Rotación de tokens sin reiniciar contenedor

### 29.2 MFA Authentik (AD-023) ✅

#### TAREA 29.2.1: Habilitar TOTP en Authentik ✅
- Grupo `ADES Admins` creado en Authentik
- Stage `ades-mfa-strict-validation` (not_configured_action=configure) en flow orden 29
- ExpressionPolicy `ades-mfa-enforce-admins` vinculada: solo corre para miembros de ADES Admins
- ADMIN, DIRECTOR, COORD_ACADEMICO: asignar a grupo ADES Admins vía Authentik Admin UI
- Backup codes disponibles (DeviceClasses incluye STATIC)

### 29.3 Licencias y Permisos de Personal (DP-006) ✅

#### TAREA 29.3.1: DDL Licencias
- Migración `040_licencias_capacitaciones.sql`
- `ades_licencias_personal` (personal_id, tipo, fecha_inicio, fecha_fin, dias, estado, motivo, sustituto_id, aprobado_por)
- `ades_capacitaciones_docente` (docente_id, nombre, institucion, fecha, duracion_hrs, tipo_cert, certificado_url)

#### TAREA 29.3.2: Backend Licencias y Capacitaciones ✅
- `api/v1/licencias.py` — CRUD con workflow aprobación
- `api/v1/capacitaciones.py` — CRUD con subida de certificado

#### TAREA 29.3.3: Frontend Licencias y Capacitaciones ✅
- `features/licencias/licencias.component.ts` — grid + form + workflow
- `features/capacitaciones/capacitaciones.component.ts` — grid + upload

---

## FASE 29: Encripción y Seguridad Avanzada (Oct-Nov 2026)

**Objetivo:** Protección de datos sensibles y cumplimiento de normativas.  
**Duración:** 35 horas  
**Prioridad:** 🔴 CRÍTICA

### 29.1 Encripción en Reposo (pgcrypto)

#### TAREA 29.1.1: Identificar Columnas Sensibles
- **Descripción:** Auditoría de qué datos requieren encripción (CURP, SSN, datos financieros).
- **Estimado:** 2 horas
- **Resultado:**
  ```
  - ades_personas.curp
  - ades_personas.rfc
  - ades_contactos_familiares.num_cuenta_bancaria
  - ades_expedientes_medicos.alergias (maybe)
  ```

#### TAREA 29.1.2: Implementación de pgcrypto
- **Descripción:** Migración que encripta columnas existentes y crea funciones de encripción/desencripción.
- **Estimado:** 8 horas
- **Checklist:**
  - [ ] Llave maestra en Vault (no en código)
  - [ ] Funciones ENCRYPT/DECRYPT en PL/pgSQL
  - [ ] Datos históricos encriptados
  - [ ] Indices en columnas encriptadas funcionan

#### TAREA 29.1.3: Integración en Modelos SQLAlchemy
- **Descripción:** Hybridproperty para transparencia de encripción.
- **Estimado:** 4 horas
- **Código:**
  ```python
  class Persona(Base):
      curp_encrypted = Column(LargeBinary)
      
      @hybrid_property
      def curp(self):
          return decrypt_curp(self.curp_encrypted)
      
      @curp.setter
      def curp(self, value):
          self.curp_encrypted = encrypt_curp(value)
  ```

---

### 29.2 Autenticación Multifactor (MFA)

#### TAREA 29.2.1: Configuración de MFA en Authentik
- **Descripción:** Habilitar TOTP (Time-based OTP) en Authentik para docentes y admin.
- **Estimado:** 3 horas
- **Checklist:**
  - [ ] Flujo TOTP configurado en Authentik
  - [ ] QR codes generados correctamente
  - [ ] Fallback codes disponibles
  - [ ] Documentación de setup para usuarios

#### TAREA 29.2.2: Forzar MFA para Roles Críticos
- **Descripción:** Política que requiere MFA para ADMIN, DIRECTOR, COORD_ACADEMICO.
- **Estimado:** 2 horas

---

### 29.3 Gestión Centralizada de Secretos (Vault)

#### TAREA 29.3.1: Instalación de HashiCorp Vault
- **Descripción:** Docker container para Vault (desarrollo y producción).
- **Estimado:** 6 horas
- **Checklist:**
  - [ ] Vault unsealed y operacional
  - [ ] Backend almacenamiento en PostgreSQL
  - [ ] API accesible desde FastAPI y Node.js

#### TAREA 29.3.2: Integración con FastAPI
- **Descripción:** Cliente Python que fetch secretos de Vault (DB credentials, API keys).
- **Estimado:** 5 horas
- **Checklist:**
  - [ ] Credenciales de BD rotadas automáticamente
  - [ ] Llave privada Ed25519 almacenada en Vault
  - [ ] Logs de acceso a Vault
  - [ ] TTL de credentials configurado

---

## FASE 30: RRHH Avanzado + Detección Inteligente (2026-06-11) ✅

**Objetivo:** Expediente laboral digital, disponibilidad docente, asistencia personal, detección automática de inconsistencias académicas.  
**Duración:** 1 sesión  
**Prioridad:** 🔴 CRÍTICA

### 30.1 Expediente Laboral Digital (DP-004) ✅

#### DDL + Backend + Frontend ✅
- Migración `041_rrhh_expediente_asistencia.sql`
- `ades_expediente_laboral` — contrato, IMSS, INFONAVIT, CURP, RFC, cedula, `documentos_urls JSONB`
- `api/v1/expediente_laboral.py` — CRUD + endpoint `/documento` para registrar URL en JSONB
- `features/expediente-laboral/expediente-laboral.component.ts` — búsqueda por persona, create/edit dialog, doc viewer

### 30.2 Disponibilidad Docente (DP-003) ✅

#### DDL + Backend + Frontend ✅
- Tabla `ades_disponibilidad_docente` (existente, slot-based: profesor_id, dia_semana SMALLINT, hora_inicio/fin TIME)
- `ades_profesores` ALTER: +`horas_semana_max`, +`horas_frente_grupo`
- `api/v1/disponibilidad.py` — bulk upsert por docente, resumen de horas, cobertura por ciclo
- `features/disponibilidad/disponibilidad.component.ts` — grid semanal visual, editor de slots por día

### 30.3 Asistencia de Personal (DP-005) ✅

#### DDL + Backend + Frontend ✅
- `ades_asistencia_personal` — persona_id, fecha, hora_entrada/salida, tipo_jornada, retardo, justificado
- `api/v1/asistencia_personal.py` — upsert ON CONFLICT, reporte mensual, justificación
- `features/asistencia-personal/asistencia-personal.component.ts` — filtros + reporte mensual con stats bar

### 30.4 Detección de Inconsistencias Académicas ✅

#### EV-007: Aprobados sin entregas
- `GET /gradebook/inconsistencias/{grupo_id}` appended to `gradebook.py`
- Detecta `es_acreditado=TRUE` + 0 entregas calificadas

#### EV-018: Candidatos a extraordinario
- `GET /gradebook/candidatos-extraordinario/{grupo_id}` appended to `gradebook.py`
- `es_acreditado=FALSE` + calificacion_final not null, join nivel_educativo para minimo_aprobatorio

#### OA-011: Alertas de rezago de planeación
- `GET /planeacion/alertas-rezago/{ciclo_id}` appended to `planeacion.py`
- `pct_cubierto < umbral (default 80%)` via HAVING

### 30.5 Comunicados Recurrentes (CO-007) ✅

- `ades_comunicados` ALTER: +`es_recurrente`, +`periodicidad`, +`proximo_envio`
- `GET /comunicados/recurrentes/pendientes` — lista pendientes de reenvío
- `POST /comunicados/{id}/programar-siguiente` — avanza `proximo_envio` según periodicidad (DIARIA/SEMANAL/QUINCENAL/MENSUAL/TRIMESTRAL)

---

## FASE 30 (ORIGINAL): Evaluación Diagnóstica e Inteligencia Predictiva (Nov-Dic 2026)

**Objetivo:** Detección temprana de riesgos académicos y necesidades especiales.  
**Duración:** 45 horas

### 30.1 Evaluación Diagnóstica Automatizada

#### TAREA 30.1.1: DDL de Evaluaciones Diagnósticas
- **Descripción:** Migración `036_evaluaciones_diagnosticas.sql`.
- **Estimado:** 3 horas

#### TAREA 30.1.2: Backend: Engine de Cuestionarios
- **Descripción:** Sistema dinámico de cuestionarios multitipo (opción múltiple, escala, texto libre).
- **Estimado:** 10 horas
- **Checklist:**
  - [ ] CRUD de cuestionarios
  - [ ] Asignación masiva por grupo
  - [ ] Respuestas anónimas o identificadas
  - [ ] Análisis automático de resultados

#### TAREA 30.1.3: Frontend: Cuestionario Interactivo
- **Descripción:** Component que presenta preguntas de forma amigable (sin distracciones).
- **Estimado:** 6 horas

---

### 30.2 Detección de Plagio (Turnitin o análisis interno)

#### TAREA 30.2.1: Integración Turnitin API (opcional)
- **Descripción:** Si hay presupuesto, integración con Turnitin para verificación de plagio.
- **Estimado:** 12 horas
- **Alternativa:** Análisis interno con cosine similarity en Python

#### TAREA 30.2.2: Backend: Análisis de Plagio
- **Descripción:** Función que compara entregas contra base de datos de trabajos previos.
- **Estimado:** 8 horas

---

### 30.3 Análisis Predictivo de Abandono

#### TAREA 30.3.1: Modelo ML (scikit-learn)
- **Descripción:** Entrenamiento de modelo con datos históricos ADES.
- **Features:** calificaciones, asistencia, conducta, edad, SES
- **Estimado:** 12 horas
- **Checklist:**
  - [ ] Dataset preparado (2020-2026)
  - [ ] Train/test split 80/20
  - [ ] Accuracy ≥ 75%
  - [ ] Feature importance documentada

#### TAREA 30.3.2: Backend: Endpoint de Predicción
- **Descripción:** Endpoint que predice riesgo de abandono para alumno X.
- **Estimado:** 4 horas

---

## FASE 31: Foros, Gamificación y Personalización (Ene-Feb 2027)

**Objetivo:** Comunidad colaborativa y engagement de usuarios.  
**Duración:** 40 horas

### 31.1 Foros por Materia

#### TAREA 31.1.1: DDL de Foros
- **Descripción:** Migración `037_foros.sql`.
- **Tablas:** `ades_foros_materia`, `ades_foro_posts`, `ades_foro_respuestas`
- **Estimado:** 3 horas

#### TAREA 31.1.2: Backend: API de Foros
- **Descripción:** Endpoints CRUD de posts y respuestas, with threading.
- **Estimado:** 8 horas

#### TAREA 31.1.3: Frontend: Componente Foro
- **Descripción:** Interfaz estilo Reddit (lista de posts, hilo detallado, responder).
- **Estimado:** 10 horas

### 31.2 Customización de Dashboards

#### TAREA 31.2.1: Widget System
- **Descripción:** Framework que permite agregar/remover widgets del dashboard por usuario.
- **Estimado:** 8 horas
- **Checklist:**
  - [ ] Persistencia de layout por usuario (Valkey)
  - [ ] Drag & drop para reordenar
  - [ ] Preset layouts por rol

---

## FASE 32: Cumplimiento Normativo y LRFD (Feb-Mar 2027)

**Objetivo:** Conformidad con leyes de protección de datos.  
**Duración:** 30 horas

### 32.1 Ley de Regulación Federal de Datos (LRFD)

#### TAREA 32.1.1: Auditoría de Compliance
- **Descripción:** Verificación de qué datos se recopilan, cómo se almacenan, quién accede.
- **Estimado:** 8 horas

#### TAREA 32.1.2: Derechos de Acceso y Portabilidad
- **Descripción:** Endpoint que permite a usuario descargar sus datos (DSAR - Data Subject Access Request).
- **Estimado:** 6 horas
- **Formato:** ZIP con JSON + PDF

#### TAREA 32.1.3: Eliminación de Datos (Derecho al Olvido)
- **Descripción:** Flujo para borrar datos de usuario retirado.
- **Estimado:** 6 horas

---

## FASE 33: Disaster Recovery y Escalabilidad (Mar-Abr 2027)

**Objetivo:** Alta disponibilidad y recuperación ante fallos.  
**Duración:** 50 horas

### 33.1 Replicación PostgreSQL

#### TAREA 33.1.1: Setup de Replica (Standby)
- **Descripción:** Configuración de streaming replication con hot standby.
- **Estimado:** 8 horas

### 33.2 CDN para Contenidos Estáticos
- **Descripción:** Integración de Cloudflare o Akamai para cachear assets.
- **Estimado:** 4 horas

---

## FASE 34: Integraciones Externas (Abr-May 2027)

**Objetivo:** Conectividad con sistemas educativos externos (SEP, UAEMEX).  
**Duración:** 35 horas

### 34.1 Integración SEP (reporte de calificaciones)

#### TAREA 34.1.1: API SEP Compliance
- **Descripción:** Generar actas en formato SEP y enviar vía web service.
- **Estimado:** 12 horas

### 34.2 Integración UAEMEX
- **Descripción:** Validación de planes de estudio CBU 2024, reportes a UAEMEX.
- **Estimado:** 10 horas

---

## FASE 35: Excelencia Operativa y Monitorizacion (May-Jun 2027)

**Objetivo:** Dashboards de monitoreo en tiempo real, alertas proactivas.  
**Duración:** 30 horas

### 35.1 Dashboards Grafana Avanzados

#### TAREA 35.1.1: Métricas de Negocio
- **Descripción:** KPIs: matriculación, retención, rendimiento académico, satisfacción.
- **Estimado:** 8 horas

#### TAREA 35.1.2: Alertas Automáticas
- **Descripción:** Grafana alerts que disparan notificaciones (email, SMS, Slack).
- **Estimado:** 6 horas

---

## Cronograma de Implementación

```
2026
├── Q3 (Jul-Sep)
│   ├── FASE 27: Consolidación Base & Recuperación ----▓▓▓▓
│   └── FASE 28 (inicio): Documentos & Expediente ----▓▓
│
├── Q4 (Oct-Dic)
│   ├── FASE 28: Documentos & Expediente ----▓▓▓▓
│   ├── FASE 29: Encripción & Seguridad ----▓▓▓▓
│   └── FASE 30 (inicio): Evaluación Diagnóstica ----▓▓
│
2027
├── Q1 (Ene-Mar)
│   ├── FASE 30: Evaluación Diagnóstica ----▓▓▓▓
│   ├── FASE 31: Foros & Personalización ----▓▓▓▓
│   └── FASE 32: Cumplimiento LRFD ----▓▓▓▓
│
├── Q2 (Abr-Jun)
│   ├── FASE 33: Disaster Recovery ----▓▓▓▓
│   ├── FASE 34: Integraciones Externas ----▓▓▓▓
│   └── FASE 35: Excelencia Operativa ----▓▓▓▓

Total: ~300 horas de desarrollo | ~15 semanas de trabajo
```

---

## Matriz de Dependencias

| Fase | Depende de | Observaciones |
|------|-----------|---|
| 27 | 1-26 | Base requerida |
| 28 | 27 | Backup debe estar en place |
| 29 | 27 | Vault para secrets |
| 30 | 28 | Paperless para documentos diagnósticos |
| 31 | 29 | MFA requerida para foros moderados |
| 32 | 31 | Cumplimiento normativo general |
| 33 | 32 | DR es requisito de compliance |
| 34 | 33 | Estabilidad previa a integraciones |
| 35 | 34 | Monitoreo de todo el stack |

---

## Recursos Requeridos

### Personal
- **1 Arquitecto** (guía general, decisiones técnicas)
- **2 Full-Stack Developers** (backend FastAPI + frontend Angular)
- **1 DBA** (PostgreSQL, backups, performance)
- **1 DevOps Engineer** (infraestructura, Docker, CI/CD)

### Hardware/Cloud
- **Servidor producción:** ARM OCI 4 cores 24GB RAM (actual) + escalar si es necesario
- **Servidor DR:** Replica standby (actualmente no existe)
- **Storage:** MinIO 100GB actual → escalable a 500GB

### Herramientas/Servicios
- **HashiCorp Vault** — gestión de secretos (FASE 29)
- **Turnitin** (opcional) — detección de plagio
- **SendGrid** o similar — envío de emails masivos
- **CloudFlare** — CDN (FASE 33)

---

## Métricas de Éxito por Fase

| Fase | Métrica | Meta |
|------|---------|------|
| 27 | RTO/RPO | < 1 hora |
| 28 | Expedientes digitalizados | 100% |
| 29 | Datos sensibles encriptados | 100% |
| 30 | Cobertura de evaluaciones | 90% alumnos |
| 31 | Engagement en foros | 50% usuarios activos |
| 32 | LRFD compliance | 100% |
| 33 | Uptime | 99.9% |
| 34 | Reportes SEP/UAEMEX | Automatizados 100% |
| 35 | Satisfacción usuario | 4.5/5.0 |

---

## Cambios de Riesgo y Mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|------------|--------|-----------|
| Pérdida de datos | Media | Crítico | Backups redundantes, DR plan, testing |
| No cumplimiento LRFD | Alta | Crítico | Auditoría temprana (FASE 32 adelantada) |
| Bajo adoption de IA | Media | Medio | Training a docentes, incentivos |
| Performance degradation | Media | Medio | Índices, caché Valkey, CDN |

---

## Guía de Ejecución con Claude Code

Para cada tarea, seguir este flujo:

1. **Setup:** Leer `.agent/STATE.md`, verificar dependencias
2. **Plan:** Desglosar tarea en subtareas atómicas
3. **Implementar:** Código + tests + documentación
4. **Deploy:** Aplicar migración, compilar imagen Docker
5. **Validar:** Checklist de aceptación completado
6. **Documentar:** Actualizar `.agent/STATE.md` y DECISIONS/

---

## Documentos de Referencia

- `.agent/CONTEXT.md` — Especificación general ADES
- `.agent/STATE.md` — Estado actual de desarrollo
- `DECISIONS/` — ADRs de arquitectura
- `docs/disaster_recovery_plan.md` — Plan DR (FASE 27)
- `docs/compliance_lrfd.md` — Normativas (FASE 32)

---

**Próxima revisión:** Septiembre 2026  
**Responsable:** Equipo de Arquitectura ADES  
**Versión:** 2.0 | Fecha: Junio 2026
