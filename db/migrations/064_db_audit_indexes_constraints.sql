-- ============================================================
-- Migración 064: Auditoría BD — Índices FK + CHECK email/tel + BRIN timestamps
-- ============================================================
-- Aplica las mejoras identificadas en el Sprint DB-AUDIT:
--   1. Índices en columnas FK sin cobertura (rendimiento de JOINs)
--   2. Índices BRIN en columnas timestamp de tablas grandes
--   3. CHECK constraints para validación de email y teléfono
--   4. Índice GIN en columnas JSONB de uso frecuente
-- ============================================================

BEGIN;

-- ── 1. ÍNDICES FK FALTANTES ─────────────────────────────────────────────────
-- Aplica CREATE INDEX IF NOT EXISTS para idempotencia.
-- Prioridad: tablas con uso frecuente en JOINs de consultas críticas.

-- ades_asistencias
CREATE INDEX IF NOT EXISTS idx_asistencias_justificacion ON ades_asistencias(justificacion_id);

-- ades_bajas
CREATE INDEX IF NOT EXISTS idx_bajas_inscripcion ON ades_bajas(inscripcion_id);
CREATE INDEX IF NOT EXISTS idx_bajas_autorizado_por ON ades_bajas(autorizado_por_id);

-- ades_cambios_grupo
CREATE INDEX IF NOT EXISTS idx_cambios_grupo_inscripcion ON ades_cambios_grupo(inscripcion_id);
CREATE INDEX IF NOT EXISTS idx_cambios_grupo_origen ON ades_cambios_grupo(grupo_origen_id);
CREATE INDEX IF NOT EXISTS idx_cambios_grupo_destino ON ades_cambios_grupo(grupo_destino_id);
CREATE INDEX IF NOT EXISTS idx_cambios_grupo_autorizado ON ades_cambios_grupo(autorizado_por_id);

-- ades_calificaciones_historico (tabla snapshot)
CREATE INDEX IF NOT EXISTS idx_cal_hist_periodo ON ades_calificaciones_historico(cal_periodo_id);
CREATE INDEX IF NOT EXISTS idx_cal_hist_grupo ON ades_calificaciones_historico(grupo_id);
CREATE INDEX IF NOT EXISTS idx_cal_hist_materia ON ades_calificaciones_historico(materia_id);

-- ades_calificaciones_periodo
CREATE INDEX IF NOT EXISTS idx_cal_per_cerrado_por ON ades_calificaciones_periodo(cerrado_por);

-- ades_calificaciones_tareas
CREATE INDEX IF NOT EXISTS idx_cal_tarea_rubrica ON ades_calificaciones_tareas(rubrica_id);

-- ades_clases
CREATE INDEX IF NOT EXISTS idx_clases_horario ON ades_clases(horario_id);
CREATE INDEX IF NOT EXISTS idx_clases_materia ON ades_clases(materia_id);

-- ades_comunicados (consultas por plantel/grupo son muy frecuentes)
CREATE INDEX IF NOT EXISTS idx_comunicados_plantel ON ades_comunicados(plantel_id);
CREATE INDEX IF NOT EXISTS idx_comunicados_grupo ON ades_comunicados(grupo_id);
CREATE INDEX IF NOT EXISTS idx_comunicados_nivel_educativo ON ades_comunicados(nivel_educativo_id);
CREATE INDEX IF NOT EXISTS idx_comunicados_creado_por ON ades_comunicados(creado_por_id);

-- ades_certificados
CREATE INDEX IF NOT EXISTS idx_certificados_ciclo ON ades_certificados(ciclo_escolar_id);
CREATE INDEX IF NOT EXISTS idx_certificados_emitido_por ON ades_certificados(emitido_por_id);

-- ades_cierre_periodo_log
CREATE INDEX IF NOT EXISTS idx_cierre_ciclo ON ades_cierre_periodo_log(ciclo_escolar_id);
CREATE INDEX IF NOT EXISTS idx_cierre_cerrado_por ON ades_cierre_periodo_log(cerrado_por);

-- ades_constancias
CREATE INDEX IF NOT EXISTS idx_constancias_ciclo ON ades_constancias(ciclo_escolar_id);
CREATE INDEX IF NOT EXISTS idx_constancias_emitida_por ON ades_constancias(emitida_por_id);

-- ades_disponibilidad_docente
CREATE INDEX IF NOT EXISTS idx_disp_docente_profesor ON ades_disponibilidad_docente(profesor_id);

-- ades_encuestas
CREATE INDEX IF NOT EXISTS idx_encuestas_plantel ON ades_encuestas(plantel_id);
CREATE INDEX IF NOT EXISTS idx_encuestas_grupo ON ades_encuestas(grupo_id);

-- ades_alertas_academicas
CREATE INDEX IF NOT EXISTS idx_alertas_ac_atendida_por ON ades_alertas_academicas(atendida_por_id);

-- ades_alertas_cumplimiento
CREATE INDEX IF NOT EXISTS idx_alertas_cum_alumno ON ades_alertas_cumplimiento(alumno_id);
CREATE INDEX IF NOT EXISTS idx_alertas_cum_plantel ON ades_alertas_cumplimiento(plantel_id);

-- ades_avance_planificacion
CREATE INDEX IF NOT EXISTS idx_avance_plan_clase ON ades_avance_planificacion(clase_id);

-- ades_asignaciones_aula
CREATE INDEX IF NOT EXISTS idx_asig_aula_clase ON ades_asignaciones_aula(clase_id);

-- ades_badge_otorgados
CREATE INDEX IF NOT EXISTS idx_badge_otorg_otorgado_por ON ades_badge_otorgados(otorgado_por);

-- ades_badges
CREATE INDEX IF NOT EXISTS idx_badges_plantel ON ades_badges(plantel_id);

-- ades_anuncios
CREATE INDEX IF NOT EXISTS idx_anuncios_plantel ON ades_anuncios(plantel_id);

-- ades_disponibilidad_aula
CREATE INDEX IF NOT EXISTS idx_disp_aula_ciclo ON ades_disponibilidad_aula(ciclo_escolar_id);

-- ades_cuotas_concepto
CREATE INDEX IF NOT EXISTS idx_cuotas_concepto_plantel ON ades_cuotas_concepto(plantel_id);
CREATE INDEX IF NOT EXISTS idx_cuotas_concepto_nivel ON ades_cuotas_concepto(nivel_educativo_id);

-- ades_cuotas_pagos
CREATE INDEX IF NOT EXISTS idx_cuotas_pagos_registrado_por ON ades_cuotas_pagos(registrado_por_id);

-- ades_direcciones
CREATE INDEX IF NOT EXISTS idx_direcciones_cp ON ades_direcciones(codigo_postal_id);
CREATE INDEX IF NOT EXISTS idx_direcciones_localidad ON ades_direcciones(localidad_id);

-- codigos_postales (tabla grande: 158K filas — FK sin índice penaliza búsquedas por CP)
CREATE INDEX IF NOT EXISTS idx_cp_estado ON ades_codigos_postales(estado_id);
CREATE INDEX IF NOT EXISTS idx_cp_municipio ON ades_codigos_postales(municipio_id);
CREATE INDEX IF NOT EXISTS idx_cp_tipo_asentamiento ON ades_codigos_postales(tipo_asentamiento_id);

-- ── 2. ÍNDICES BRIN EN TIMESTAMPS (tablas de log/auditoría) ─────────────────
-- BRIN es ultraligero para series de tiempo insertadas secuencialmente.
-- Uso: logs, historial, auditoría. 128 páginas por rango (default).

CREATE INDEX IF NOT EXISTS idx_brin_audit_log_ts
  ON ades_audit_log USING BRIN (fecha_creacion);

CREATE INDEX IF NOT EXISTS idx_brin_asistencias_ts
  ON ades_asistencias USING BRIN (fecha_creacion);

CREATE INDEX IF NOT EXISTS idx_brin_alertas_ts
  ON ades_alertas_academicas USING BRIN (fecha_creacion);

CREATE INDEX IF NOT EXISTS idx_brin_notif_ts
  ON ades_notificaciones_sistema USING BRIN (fecha_creacion);

CREATE INDEX IF NOT EXISTS idx_brin_log_auditoria_ts
  ON auditoria.log_auditoria USING BRIN (recorddatetime);

-- ── 3. ÍNDICE GIN en JSONB frecuente ────────────────────────────────────────
-- ia_recomendacion en lp_asignaciones (búsqueda por atributos de recomendación IA)
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_name='ades_lp_asignaciones' AND column_name='ia_recomendacion') THEN
    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_lp_asig_ia_gin ON ades_lp_asignaciones USING GIN (ia_recomendacion)';
  END IF;
END $$;

-- ── 4. LIMPIEZA DE DATOS INVÁLIDOS ──────────────────────────────────────────
-- Antes de agregar constraints, limpiar datos que no los cumplen.
-- Se guarda el valor original en notas de auditoría implícita (se NULL-iza, no se borra).

-- Limpiar emails inválidos en ades_personas (no contienen @ o dominio)
UPDATE ades_personas
SET email_personal = NULL
WHERE email_personal IS NOT NULL
  AND email_personal NOT LIKE '%@%.%';

-- Limpiar teléfonos inválidos en ades_personas (no son 10 dígitos normalizados)
UPDATE ades_personas
SET telefono = NULL
WHERE telefono IS NOT NULL
  AND regexp_replace(telefono, '[\s\-\(\)\.]', '', 'g') !~ '^\d{10}$';

-- Limpiar emails inválidos en ades_persona_contactos
UPDATE ades_persona_contactos
SET valor = NULL, is_active = FALSE
WHERE medio = 'EMAIL'
  AND valor IS NOT NULL
  AND valor NOT LIKE '%@%.%';

-- Limpiar teléfonos inválidos en ades_persona_contactos
UPDATE ades_persona_contactos
SET valor = NULL, is_active = FALSE
WHERE medio IN ('CELULAR', 'FIJO', 'WHATSAPP')
  AND valor IS NOT NULL
  AND regexp_replace(valor, '[\s\-\(\)\.]', '', 'g') !~ '^\d{10}$';

-- Limpiar emails inválidos en ades_contactos_familiares
UPDATE ades_contactos_familiares
SET email = NULL
WHERE email IS NOT NULL
  AND email NOT LIKE '%@%.%';

-- Limpiar teléfonos inválidos en ades_contactos_familiares (columnas: telefono_principal, telefono_trabajo)
UPDATE ades_contactos_familiares
SET telefono_principal = NULL
WHERE telefono_principal IS NOT NULL
  AND regexp_replace(telefono_principal, '[\s\-\(\)\.]', '', 'g') !~ '^\d{10}$';

UPDATE ades_contactos_familiares
SET telefono_trabajo = NULL
WHERE telefono_trabajo IS NOT NULL
  AND regexp_replace(telefono_trabajo, '[\s\-\(\)\.]', '', 'g') !~ '^\d{10}$';

-- ── 5. CHECK CONSTRAINTS PARA EMAIL Y TELÉFONO ─────────────────────────────
-- Ahora que los datos están limpios, agregar constraints de validación.

-- ades_personas.email_personal: must be NULL or contain @
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_personas_email_personal' AND conrelid = 'ades_personas'::regclass
  ) THEN
    ALTER TABLE ades_personas
      ADD CONSTRAINT chk_personas_email_personal
        CHECK (email_personal IS NULL OR email_personal LIKE '%@%.%');
  END IF;
END $$;

-- ades_personas.telefono: must be NULL or 10 digits (after stripping spaces/dashes)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_personas_telefono' AND conrelid = 'ades_personas'::regclass
  ) THEN
    ALTER TABLE ades_personas
      ADD CONSTRAINT chk_personas_telefono
        CHECK (telefono IS NULL OR regexp_replace(telefono, '[\s\-\(\)\.]', '', 'g') ~ '^\d{10}$');
  END IF;
END $$;

-- ades_persona_contactos.valor: validate by medio type
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_pc_valor_email' AND conrelid = 'ades_persona_contactos'::regclass
  ) THEN
    ALTER TABLE ades_persona_contactos
      ADD CONSTRAINT chk_pc_valor_email
        CHECK (
          medio <> 'EMAIL'
          OR (valor IS NOT NULL AND valor LIKE '%@%.%')
        );
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_pc_valor_telefono' AND conrelid = 'ades_persona_contactos'::regclass
  ) THEN
    ALTER TABLE ades_persona_contactos
      ADD CONSTRAINT chk_pc_valor_telefono
        CHECK (
          medio NOT IN ('CELULAR', 'FIJO', 'WHATSAPP')
          OR (valor IS NOT NULL AND regexp_replace(valor, '[\s\-\(\)\.]', '', 'g') ~ '^\d{10}$')
        );
  END IF;
END $$;

-- ades_contactos_familiares: email + phone (telefono_principal, telefono_trabajo)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_cont_fam_email' AND conrelid = 'ades_contactos_familiares'::regclass
  ) THEN
    ALTER TABLE ades_contactos_familiares
      ADD CONSTRAINT chk_cont_fam_email
        CHECK (email IS NULL OR email LIKE '%@%.%');
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_cont_fam_tel_principal' AND conrelid = 'ades_contactos_familiares'::regclass
  ) THEN
    ALTER TABLE ades_contactos_familiares
      ADD CONSTRAINT chk_cont_fam_tel_principal
        CHECK (
          telefono_principal IS NULL
          OR regexp_replace(telefono_principal, '[\s\-\(\)\.]', '', 'g') ~ '^\d{10}$'
        );
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_cont_fam_tel_trabajo' AND conrelid = 'ades_contactos_familiares'::regclass
  ) THEN
    ALTER TABLE ades_contactos_familiares
      ADD CONSTRAINT chk_cont_fam_tel_trabajo
        CHECK (
          telefono_trabajo IS NULL
          OR regexp_replace(telefono_trabajo, '[\s\-\(\)\.]', '', 'g') ~ '^\d{10}$'
        );
  END IF;
END $$;

-- ── 6. ÍNDICES DE BÚSQUEDA FRECUENTE (no FK) ────────────────────────────────
-- Basado en los endpoints más consultados del BFF

-- Búsqueda de alumnos por CURP (único identificador oficial)
CREATE INDEX IF NOT EXISTS idx_personas_curp ON ades_personas(curp) WHERE curp IS NOT NULL;

-- Búsqueda de usuarios por oidc_sub (autenticación Authentik — cada request)
CREATE INDEX IF NOT EXISTS idx_usuarios_oidc_sub ON ades_usuarios(oidc_sub) WHERE oidc_sub IS NOT NULL;

-- Búsqueda de profesores por email_personal
CREATE INDEX IF NOT EXISTS idx_personas_email ON ades_personas(email_personal) WHERE email_personal IS NOT NULL;

-- Búsqueda de clases activas por grupo y fecha (asistencias)
CREATE INDEX IF NOT EXISTS idx_clases_grupo_fecha
  ON ades_clases(grupo_id, fecha_clase)
  WHERE is_active = TRUE;

-- Búsqueda de inscripciones activas por estudiante (JOIN frecuente)
CREATE INDEX IF NOT EXISTS idx_inscripciones_activas
  ON ades_inscripciones(estudiante_id, is_active)
  WHERE is_active = TRUE;

-- Historial de alertas activas por estudiante
CREATE INDEX IF NOT EXISTS idx_alertas_activas
  ON ades_alertas_academicas(estudiante_id, tipo_alerta)
  WHERE is_active = TRUE;

-- Reportes de conducta activos por estudiante
CREATE INDEX IF NOT EXISTS idx_conducta_activos
  ON ades_reportes_conducta(estudiante_id)
  WHERE is_active = TRUE;

-- Bajas activas por estudiante (consulta en movilidad)
CREATE INDEX IF NOT EXISTS idx_bajas_activas
  ON ades_bajas(estudiante_id, tipo_baja)
  WHERE is_active = TRUE;

-- Comunicados activos por plantel (publicación más reciente primero)
CREATE INDEX IF NOT EXISTS idx_comunicados_activos_plantel
  ON ades_comunicados(plantel_id, fecha_publicacion DESC)
  WHERE is_active = TRUE;

-- ── 7. COMENTARIOS EN TABLAS CORE ──────────────────────────────────────────

COMMENT ON TABLE ades_personas IS
  'Tabla maestra de personas físicas del sistema. Una persona puede ser alumno, profesor, tutor o personal administrativo. Contiene datos de identidad, nacimiento y contacto. Columnas sensibles: curp, rfc, email_personal.';

COMMENT ON COLUMN ades_personas.curp IS
  'Clave Única de Registro de Población (18 caracteres). Identificador oficial SEP. Único por persona. Formato validado externamente al insertar.';

COMMENT ON COLUMN ades_personas.genero IS
  'Género legal: M=Masculino, F=Femenino, X=No binario (CURP tipo X). No confundir con genero_autopercibido.';

COMMENT ON COLUMN ades_personas.nombre_social IS
  'Nombre preferido en el aula. Se usa en listas de asistencia si está definido. NULL si coincide con nombre legal.';

COMMENT ON COLUMN ades_personas.email_personal IS
  'Correo electrónico personal. Formato validado por constraint chk_personas_email_personal.';

COMMENT ON COLUMN ades_personas.telefono IS
  'Teléfono principal (10 dígitos). Validado por constraint chk_personas_telefono. Columna legacy: preferir ades_persona_contactos para múltiples medios.';

COMMENT ON TABLE ades_estudiantes IS
  'Datos académicos y escolares específicos del alumno. Hereda identidad de ades_personas via persona_id. Incluye matrícula única por plantel, nivel socioeconómico, datos de beca y procedencia escolar.';

COMMENT ON COLUMN ades_estudiantes.matricula IS
  'Clave de matrícula institucional única. Formato: MAT-{plantel}-{nivel}-{grado}{grupo}-{seq}. Asignada al registrar al alumno.';

COMMENT ON TABLE ades_profesores IS
  'Datos laborales y académicos del docente. Hereda identidad de ades_personas via persona_id. Incluye RFC, cédula profesional, materias que puede impartir y carga horaria.';

COMMENT ON TABLE ades_inscripciones IS
  'Registro de la inscripción de un alumno a un grupo en un ciclo escolar. Una inscripción activa (is_active=TRUE) es el estado actual del alumno. El historial de cambios está en ades_cambios_grupo.';

COMMENT ON COLUMN ades_inscripciones.is_active IS
  'TRUE = inscripción vigente. FALSE = baja, traslado o cambio de grupo. Solo debe existir una inscripción activa por alumno en un ciclo dado.';

COMMENT ON TABLE ades_calificaciones_periodo IS
  'Calificación acumulada del alumno por materia y período de evaluación. La columna calificacion_calculada la llena la función calcular_calificacion_periodo(). El ajuste manual (ajuste_manual) lo aplica el director. La columna es_acreditado se recalcula por trigger.';

COMMENT ON COLUMN ades_calificaciones_periodo.cerrada IS
  'TRUE = período cerrado formalmente. No se aceptan más modificaciones. Solo puede cerrar el DIRECTOR o COORDINADOR.';

COMMENT ON COLUMN ades_calificaciones_periodo.es_acreditado IS
  'Recalculado automáticamente por trg_calificacion_periodo_acreditado. Umbral dinámico: SEP=6.0 sobre 10, UAEMEX=60.0 sobre 100.';

COMMENT ON TABLE ades_asistencias IS
  'Registro de asistencia alumno-clase. estatus_asistencia: PRESENTE, FALTA, TARDE, JUSTIFICADA. El trigger trg_recalcular_desde_asistencia recalcula el componente asistencia en ades_calificaciones_periodo al insertar/actualizar.';

COMMENT ON TABLE ades_grupos IS
  'Grupo escolar por nivel/grado/ciclo. capacidad_maxima controla el cupo para cambios de grupo. nombre_grupo = grado + letra (ej: 1A, 2B).';

COMMENT ON TABLE ades_planteles IS
  'Planteles físicos del Instituto Nevadi: Metepec (Primaria+Secundaria+Preparatoria), Tenancingo (Primaria+Secundaria+Preparatoria), Ixtapan de la Sal (Primaria+Secundaria, Preparatoria proyectada).';

COMMENT ON TABLE ades_ciclos_escolares IS
  'Ciclo escolar por nivel educativo y plantel. es_vigente=TRUE identifica el ciclo activo que usan los filtros globales. Solo debe existir un ciclo vigente por nivel+plantel.';

COMMENT ON TABLE ades_usuarios IS
  'Cuentas de acceso al sistema ADES. Se vinculan con Authentik via oidc_sub. nivel_acceso: 1=Admin Global, 2=Director, 3=Coordinador, 4=Docente, 5=Apoyo/Prefecto. Un usuario puede estar asociado a un plantel_id específico (null=acceso global).';

COMMENT ON COLUMN ades_usuarios.nivel_acceso IS
  'Nivel de acceso RBAC: 1=ADMIN_GLOBAL, 2=DIRECTOR, 3=COORDINADOR, 4=DOCENTE, 5=PREFECTO/APOYO. Los guards de Angular y Spring Boot usan este valor para controlar rutas y endpoints.';

COMMENT ON TABLE ades_bajas IS
  'Registro de bajas, traslados y deserciones de alumnos. tipo_baja: TEMPORAL (reactivable), DEFINITIVA (permanente), TRASLADO (a otro plantel), DESERCION. Solo DIRECTOR puede autorizar.';

COMMENT ON TABLE ades_cambios_grupo IS
  'Historial de cambios de grupo (movilidad interna). Registra el grupo origen, destino, inscripción modificada y autorización. Solo COORDINADOR+ puede autorizar.';

COMMENT ON TABLE ades_certificados IS
  'Certificados digitales firmados con Ed25519. Cada certificado tiene folio único verificable en /verificar/:folio. estado_firma: PENDIENTE→FIRMADO. La llave privada NO se almacena en BD — vive en .env (FIRMA_CLAVE_PRIVADA_HEX).';

COMMENT ON TABLE ades_persona_contactos IS
  'Medios de contacto estructurados por persona (normalización de ades_personas.telefono y email_personal). medio: CELULAR, FIJO, WHATSAPP, EMAIL, TELEGRAM, FAX, OTRO. Validación de formato por constraints chk_pc_valor_email y chk_pc_valor_telefono.';

COMMENT ON TABLE ades_contactos_familiares IS
  'Familiares, tutores y contactos de emergencia de un alumno. es_tutor_legal=TRUE identifica a quien tiene custodia legal. telefono_principal y telefono_trabajo validados por constraints. Ordenados por columna prioridad.';

COMMENT ON TABLE ades_direcciones IS
  'Domicilios de personas (alumnos, profesores, tutores). Integra catálogo SEPOMEX via codigo_postal_id y localidad_id. Incluye geocodificación opcional (latitud/longitud) via Nominatim.';

COMMENT ON TABLE ades_codigos_postales IS
  'Catálogo SEPOMEX: 158,088 asentamientos (colonias, ejidos, fraccionamientos) de México. Alimenta la cascada CP→Colonia→Municipio→Estado en formularios de domicilio.';

COMMENT ON TABLE ades_alertas_academicas IS
  'Alertas de riesgo académico generadas automáticamente. tipo_alerta: REPROBACION (promedio<6.0), AUSENTISMO (<80% asistencia), RIESGO_ALTO (IA). El campo ia_analisis contiene el análisis generado por Claude.';

COMMENT ON TABLE ades_ai_conversaciones IS
  'Historial de mensajes del asistente pedagógico IA. Cada fila es un mensaje (role: user/assistant). Contexto de la conversación se recupera por sesion_id.';

COMMENT ON TABLE ades_lp_asignaciones IS
  'Asignaciones de Learning Paths a alumnos. ia_recomendacion JSONB contiene el análisis de Claude (fortalezas, áreas mejora, estrategias, recursos priorizados).';

COMMENT ON TABLE ades_audit_log IS
  'Log inmutable de mutaciones del sistema. Cada fila tiene hash MD5 encadenado para detectar alteración directa. NO hacer UPDATE/DELETE. Retención mínima: 5 años (SEP).';

COMMENT ON TABLE auditoria.log_auditoria IS
  'Log de auditoría nivel 2 (AFTER INSERT/UPDATE/DELETE en producción). Hash encadenado por uuid_ref para detectar alteración directa. Solo activo cuando audit_aiud está asignado via auditoria.asignar_triggers().';

-- ── 8. COMENTARIOS EN FUNCIONES CORE ────────────────────────────────────────

COMMENT ON FUNCTION calcular_calificacion_periodo(UUID, UUID, UUID, UUID) IS
  'Recalcula calificacion_calculada en ades_calificaciones_periodo. Args: p_alumno_id, p_grupo_id, p_materia_id, p_periodo_id. Pondera: examen, tarea, proyecto, asistencia, comportamiento según esquema de ponderación activo. Actualiza también es_acreditado via trigger.';

COMMIT;

-- ── Reporte de índices creados ───────────────────────────────────────────────
SELECT
  indexname,
  tablename,
  indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;
