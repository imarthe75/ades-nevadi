-- ============================================================
-- Migración 042: Operatividad Avanzada
-- CUs: SB-006 (condiciones crónicas), SB-007 (emergencias),
--       OA-003 (justificación faltas), PE-016 (no-adeudo),
--       CO-005 (reporte lectura), AC-018/019 (horarios dinámicos),
--       DP-010 (reasignación docente)
-- Fecha: 2026-06-11
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. Condiciones crónicas de alumnos (SB-006/007)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_condiciones_cronicas (
    id                  UUID        NOT NULL DEFAULT uuidv7(),
    alumno_id           UUID        NOT NULL REFERENCES ades_estudiantes(id) ON DELETE CASCADE,
    tipo_condicion      VARCHAR(60) NOT NULL,   -- EPILEPSIA, DIABETES, ASMA, ALERGIA, CARDIACA, OTRA
    descripcion         TEXT        NOT NULL,
    medicacion_nombre   VARCHAR(150),
    dosis               VARCHAR(100),
    frecuencia          VARCHAR(100),
    alergias            TEXT,
    medico_responsable  VARCHAR(150),
    telefono_medico     VARCHAR(15),
    activa              BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría canónica
    ref                 UUID        NOT NULL DEFAULT uuidv7(),
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    TEXT        NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion TEXT       NOT NULL DEFAULT CURRENT_USER,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT ades_condiciones_cronicas_pkey PRIMARY KEY (id),
    CONSTRAINT ades_condiciones_cronicas_ref_key UNIQUE (ref),
    CONSTRAINT chk_condicion_tipo CHECK (
        tipo_condicion IN ('EPILEPSIA','DIABETES','ASMA','ALERGIA','CARDIACA','HIPERTENSION','DISCAPACIDAD_VISUAL','DISCAPACIDAD_AUDITIVA','OTRA')
    )
);

CREATE INDEX IF NOT EXISTS idx_cond_alumno ON ades_condiciones_cronicas(alumno_id) WHERE is_active;
CREATE INDEX IF NOT EXISTS idx_cond_tipo   ON ades_condiciones_cronicas(tipo_condicion) WHERE activa;

COMMENT ON TABLE ades_condiciones_cronicas IS 'Condiciones de salud crónicas de alumnos para alertas médicas (SB-006/007)';

SELECT auditoria.asignar_biu('public.ades_condiciones_cronicas');

-- ─────────────────────────────────────────────────────────────
-- 2. Justificaciones de faltas (OA-003)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_justificaciones_falta (
    id                  UUID        NOT NULL DEFAULT uuidv7(),
    asistencia_id       UUID        NOT NULL REFERENCES ades_asistencias(id) ON DELETE CASCADE,
    tipo_justificacion  VARCHAR(30) NOT NULL DEFAULT 'MEDICA',
                        -- MEDICA, FAMILIAR, DEPORTIVA, CULTURAL, ADMINISTRATIVA, OTRA
    motivo              TEXT        NOT NULL,
    documento_url       VARCHAR(500),
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
                        -- PENDIENTE, APROBADA, RECHAZADA
    aprobada_por        UUID        REFERENCES ades_usuarios(id),
    fecha_resolucion    TIMESTAMPTZ,
    motivo_rechazo      TEXT,
    -- auditoría canónica
    ref                 UUID        NOT NULL DEFAULT uuidv7(),
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    TEXT        NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion TEXT       NOT NULL DEFAULT CURRENT_USER,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT ades_justificaciones_falta_pkey PRIMARY KEY (id),
    CONSTRAINT ades_justificaciones_falta_ref_key UNIQUE (ref),
    CONSTRAINT chk_just_tipo CHECK (
        tipo_justificacion IN ('MEDICA','FAMILIAR','DEPORTIVA','CULTURAL','ADMINISTRATIVA','OTRA')
    ),
    CONSTRAINT chk_just_estado CHECK (estado IN ('PENDIENTE','APROBADA','RECHAZADA'))
);

CREATE INDEX IF NOT EXISTS idx_just_asistencia ON ades_justificaciones_falta(asistencia_id);
CREATE INDEX IF NOT EXISTS idx_just_estado     ON ades_justificaciones_falta(estado) WHERE is_active;

COMMENT ON TABLE ades_justificaciones_falta IS 'Justificaciones de faltas de alumnos con workflow aprobación (OA-003)';

SELECT auditoria.asignar_biu('public.ades_justificaciones_falta');

-- Vincular asistencia a su justificación (nullable)
ALTER TABLE ades_asistencias
    ADD COLUMN IF NOT EXISTS justificacion_id UUID REFERENCES ades_justificaciones_falta(id);

-- ─────────────────────────────────────────────────────────────
-- 3. Horarios: motivo de cambio dinámico (AC-018)
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_horarios
    ADD COLUMN IF NOT EXISTS motivo_cambio   TEXT,
    ADD COLUMN IF NOT EXISTS fecha_cambio    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS es_temporal     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fecha_fin_temp  DATE;

COMMENT ON COLUMN ades_horarios.motivo_cambio  IS 'Razón de cambio dinámico (AC-018)';
COMMENT ON COLUMN ades_horarios.es_temporal    IS 'TRUE si el cambio es temporal (enfermedad, evento)';
COMMENT ON COLUMN ades_horarios.fecha_fin_temp IS 'Fecha hasta la que aplica si es_temporal=TRUE';

-- ─────────────────────────────────────────────────────────────
-- 4. Vista: conflictos de doble asignación (AC-019)
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW v_conflictos_horario AS
WITH slots AS (
    SELECT
        h.id,
        h.ciclo_escolar_id,
        h.dia_semana,
        h.hora_inicio,
        h.hora_fin,
        h.profesor_id,
        h.aula_id,
        h.grupo_id,
        h.is_active
    FROM ades_horarios h
    WHERE h.is_active = TRUE
)
SELECT
    a.id            AS horario_a_id,
    b.id            AS horario_b_id,
    a.ciclo_escolar_id,
    a.dia_semana,
    a.hora_inicio,
    a.hora_fin,
    CASE
        WHEN a.docente_id IS NOT NULL AND a.docente_id = b.docente_id THEN 'DOCENTE'
        WHEN a.aula_id    IS NOT NULL AND a.aula_id    = b.aula_id    THEN 'AULA'
        WHEN a.grupo_id   IS NOT NULL AND a.grupo_id   = b.grupo_id   THEN 'GRUPO'
    END             AS tipo_conflicto,
    a.docente_id,
    a.aula_id,
    a.grupo_id
FROM slots a
JOIN slots b ON (
    a.ciclo_escolar_id = b.ciclo_escolar_id
    AND a.dia_semana   = b.dia_semana
    AND a.id           < b.id
    AND a.hora_inicio  < b.hora_fin
    AND b.hora_inicio  < a.hora_fin
    AND (
        (a.docente_id IS NOT NULL AND a.docente_id = b.docente_id)
        OR (a.aula_id IS NOT NULL AND a.aula_id = b.aula_id)
        OR (a.grupo_id IS NOT NULL AND a.grupo_id = b.grupo_id)
    )
);

COMMENT ON VIEW v_conflictos_horario IS 'Detección de doble asignación docente/aula/grupo en horarios (AC-019)';
