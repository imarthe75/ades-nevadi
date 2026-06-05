-- =============================================================================
-- Migración 012 — Correcciones de esquema
--
--   1. Mover tipo_sangre + alergias de ades_estudiantes → ades_expedientes_medicos
--      (ya existían ahí; la mig.011 los duplicó por error)
--   2. Enriquecer ades_expedientes_medicos con campos médicos faltantes
--   3. Agregar datos de contacto a ades_contactos_familiares (ya tiene 1,980 filas)
--   4. Agregar inasistencias + justificadas a ades_calificaciones_periodo (boleta SEP)
--   5. Agregar aula_id a ades_grupos (salón asignado al grupo)
--   6. Eliminar ades_contactos_emergencia (vacía, redundante con contactos_familiares)
--   7. Índice de vinculación PADRE_FAMILIA → alumno
-- =============================================================================

-- ── 1. Eliminar columnas duplicadas de ades_estudiantes ─────────────────────
-- tipo_sangre y alergias ya existen en ades_expedientes_medicos (tabla correcta)
ALTER TABLE ades_estudiantes
  DROP COLUMN IF EXISTS tipo_sangre,
  DROP COLUMN IF EXISTS alergias;

-- ── 2. Enriquecer ades_expedientes_medicos ───────────────────────────────────
ALTER TABLE ades_expedientes_medicos
  ADD COLUMN IF NOT EXISTS nss                   VARCHAR(11),
  ADD COLUMN IF NOT EXISTS discapacidad          TEXT,
  ADD COLUMN IF NOT EXISTS seguro_medico_tipo    VARCHAR(30),   -- IMSS ISSSTE PRIVADO NINGUNO
  ADD COLUMN IF NOT EXISTS seguro_medico_numero  VARCHAR(50),
  ADD COLUMN IF NOT EXISTS vacunas_al_dia        BOOLEAN DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS padecimiento_cronico  BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS requiere_medicacion   BOOLEAN DEFAULT FALSE;

-- Asegurar que existe un expediente médico para cada estudiante (trigger o inserción lazy)
-- Por ahora, índice único para garantizar 1:1
CREATE UNIQUE INDEX IF NOT EXISTS idx_expediente_medico_estudiante
  ON ades_expedientes_medicos(estudiante_id)
  WHERE is_active = TRUE;

-- ── 3. Enriquecer ades_contactos_familiares ──────────────────────────────────
-- La tabla ya tiene: estudiante_id, persona_id, parentesco, es_tutor_legal,
-- es_contacto_emergencia, puede_recoger
-- Agrega campos de detalle del contacto (para cuando no existe persona registrada)
ALTER TABLE ades_contactos_familiares
  ADD COLUMN IF NOT EXISTS nombre_completo   VARCHAR(200),
  ADD COLUMN IF NOT EXISTS telefono_principal VARCHAR(15),
  ADD COLUMN IF NOT EXISTS telefono_trabajo  VARCHAR(15),
  ADD COLUMN IF NOT EXISTS email             VARCHAR(255),
  ADD COLUMN IF NOT EXISTS ocupacion         VARCHAR(100),
  ADD COLUMN IF NOT EXISTS nivel_estudios    VARCHAR(30),   -- PRIMARIA SECUNDARIA BACHILLERATO LICENCIATURA+
  ADD COLUMN IF NOT EXISTS rfc               VARCHAR(13),
  ADD COLUMN IF NOT EXISTS prioridad         SMALLINT DEFAULT 1; -- orden de llamada en emergencia

-- Índice para buscar los contactos de un alumno rápidamente
CREATE INDEX IF NOT EXISTS idx_contactos_fam_estudiante
  ON ades_contactos_familiares(estudiante_id)
  WHERE is_active = TRUE;

-- Índice para encontrar los alumnos de un tutor (PADRE_FAMILIA login)
CREATE INDEX IF NOT EXISTS idx_contactos_fam_persona
  ON ades_contactos_familiares(persona_id)
  WHERE is_active = TRUE AND es_tutor_legal = TRUE;

-- ── 4. Boleta SEP: inasistencias por periodo por materia ─────────────────────
ALTER TABLE ades_calificaciones_periodo
  ADD COLUMN IF NOT EXISTS inasistencias    SMALLINT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS justificadas     SMALLINT DEFAULT 0;

-- ── 5. Aula asignada al grupo (salón base) ───────────────────────────────────
ALTER TABLE ades_grupos
  ADD COLUMN IF NOT EXISTS aula_id UUID REFERENCES ades_aulas(id) ON DELETE SET NULL;

-- ── 6. Eliminar tabla redundante (vacía) ─────────────────────────────────────
DROP TABLE IF EXISTS ades_contactos_emergencia;

-- ── 7. Vista helper: alumnos visibles por un usuario PADRE_FAMILIA ──────────
CREATE OR REPLACE VIEW v_tutor_alumnos AS
SELECT
  u.id          AS usuario_id,
  u.persona_id  AS tutor_persona_id,
  cf.estudiante_id,
  cf.parentesco,
  cf.es_tutor_legal,
  cf.puede_recoger
FROM ades_usuarios u
JOIN ades_contactos_familiares cf
  ON cf.persona_id = u.persona_id
 AND cf.is_active  = TRUE
WHERE u.is_active = TRUE;

COMMENT ON VIEW v_tutor_alumnos IS
  'Vincula usuarios PADRE_FAMILIA con sus alumnos via ades_contactos_familiares.';
