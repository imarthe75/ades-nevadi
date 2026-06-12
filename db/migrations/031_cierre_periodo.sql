-- =============================================================================
-- Migración 031: Cierre Formal de Período Académico (EV-006)
-- =============================================================================
-- Agrega: cerrado_por, tabla de log de cierres, tabla histórica de snapshot,
--         función validar_cierre_periodo().
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Columna cerrado_por en ades_calificaciones_periodo
--    (cerrada y fecha_cierre ya existen desde migración 007)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE ades_calificaciones_periodo
    ADD COLUMN IF NOT EXISTS cerrado_por UUID REFERENCES ades_usuarios(id) ON DELETE SET NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla de log de cierres formales
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_cierre_periodo_log (
    id                        UUID        PRIMARY KEY DEFAULT uuidv7(),
    periodo_evaluacion_id     UUID        NOT NULL REFERENCES ades_periodos_evaluacion(id),
    grupo_id                  UUID        NOT NULL REFERENCES ades_grupos(id),
    ciclo_escolar_id          UUID        REFERENCES ades_ciclos_escolares(id),
    calificaciones_cerradas   INT         NOT NULL DEFAULT 0,
    alumnos_sin_calificacion  INT         NOT NULL DEFAULT 0,
    estado                    VARCHAR(20) NOT NULL DEFAULT 'CERRADO'
                                          CHECK (estado IN ('ABIERTO', 'EN_CIERRE', 'CERRADO')),
    cerrado_por               UUID        REFERENCES ades_usuarios(id) ON DELETE SET NULL,
    fecha_cierre              TIMESTAMPTZ NOT NULL DEFAULT now(),
    notas                     TEXT,
    row_version               INT         NOT NULL DEFAULT 1,
    is_active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion            TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ades_cierre_periodo_log IS
    'Registro formal de cada cierre de período por grupo. Inmutable una vez cerrado.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Snapshot histórico de calificaciones al cierre
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_calificaciones_historico (
    id                    UUID        PRIMARY KEY DEFAULT uuidv7(),
    cierre_id             UUID        NOT NULL REFERENCES ades_cierre_periodo_log(id),
    cal_periodo_id        UUID        NOT NULL REFERENCES ades_calificaciones_periodo(id),
    estudiante_id         UUID        NOT NULL REFERENCES ades_estudiantes(id),
    grupo_id              UUID        NOT NULL REFERENCES ades_grupos(id),
    materia_id            UUID        NOT NULL REFERENCES ades_materias(id),
    periodo_evaluacion_id UUID        NOT NULL REFERENCES ades_periodos_evaluacion(id),
    calificacion_final    NUMERIC(5,2),
    calificacion_calculada NUMERIC(5,2),
    ajuste_manual         NUMERIC(5,2),
    es_acreditado         BOOLEAN,
    snapshot_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cal_hist_cierre
    ON ades_calificaciones_historico (cierre_id);
CREATE INDEX IF NOT EXISTS idx_cal_hist_estudiante
    ON ades_calificaciones_historico (estudiante_id, periodo_evaluacion_id);

COMMENT ON TABLE ades_calificaciones_historico IS
    'Snapshot inmutable de calificaciones al momento del cierre formal de cada período.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Función: validar_cierre_periodo(periodo_id, grupo_id)
--    Retorna: puede_cerrar BOOLEAN, alumnos_faltantes INT, detalles JSONB
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION validar_cierre_periodo(
    p_periodo_id UUID,
    p_grupo_id   UUID
)
RETURNS TABLE (
    puede_cerrar             BOOLEAN,
    alumnos_faltantes        INT,
    materias_incompletas     INT,
    detalles                 JSONB
)
LANGUAGE plpgsql AS $$
DECLARE
    v_total_esperadas   INT;
    v_con_calificacion  INT;
    v_ya_cerradas       INT;
BEGIN
    -- Total de filas ades_calificaciones_periodo esperadas para este grupo+período
    SELECT COUNT(*)
      INTO v_total_esperadas
      FROM ades_calificaciones_periodo cp
     WHERE cp.grupo_id              = p_grupo_id
       AND cp.periodo_evaluacion_id = p_periodo_id
       AND cp.is_active             = TRUE;

    -- De esas, cuántas ya tienen calificación_final no nula
    SELECT COUNT(*)
      INTO v_con_calificacion
      FROM ades_calificaciones_periodo cp
     WHERE cp.grupo_id              = p_grupo_id
       AND cp.periodo_evaluacion_id = p_periodo_id
       AND cp.is_active             = TRUE
       AND cp.calificacion_final    IS NOT NULL;

    -- Cuántas ya estaban cerradas
    SELECT COUNT(*)
      INTO v_ya_cerradas
      FROM ades_calificaciones_periodo cp
     WHERE cp.grupo_id              = p_grupo_id
       AND cp.periodo_evaluacion_id = p_periodo_id
       AND cp.is_active             = TRUE
       AND cp.cerrada               = TRUE;

    RETURN QUERY
    SELECT
        -- puede cerrar si todas tienen calificación y ninguna ya está cerrada
        (v_total_esperadas > 0
         AND v_con_calificacion = v_total_esperadas
         AND v_ya_cerradas = 0)                                         AS puede_cerrar,

        (v_total_esperadas - v_con_calificacion)::INT                  AS alumnos_faltantes,

        -- materias incompletas = materias con al menos 1 alumno sin cal
        (SELECT COUNT(DISTINCT cp2.materia_id)
           FROM ades_calificaciones_periodo cp2
          WHERE cp2.grupo_id              = p_grupo_id
            AND cp2.periodo_evaluacion_id = p_periodo_id
            AND cp2.is_active             = TRUE
            AND cp2.calificacion_final    IS NULL)::INT                 AS materias_incompletas,

        jsonb_build_object(
            'total_esperadas',    v_total_esperadas,
            'con_calificacion',   v_con_calificacion,
            'ya_cerradas',        v_ya_cerradas,
            'faltantes',          v_total_esperadas - v_con_calificacion,
            -- lista de alumnos sin calificación (máx 50)
            'alumnos_sin_cal', (
                SELECT jsonb_agg(jsonb_build_object(
                    'alumno',  p.nombre || ' ' || p.apellido_paterno,
                    'materia', m.nombre_materia
                ))
                FROM ades_calificaciones_periodo cp3
                JOIN ades_estudiantes est ON est.id  = cp3.estudiante_id
                JOIN ades_personas    p   ON p.id    = est.persona_id
                JOIN ades_materias    m   ON m.id    = cp3.materia_id
               WHERE cp3.grupo_id              = p_grupo_id
                 AND cp3.periodo_evaluacion_id = p_periodo_id
                 AND cp3.is_active             = TRUE
                 AND cp3.calificacion_final    IS NULL
               LIMIT 50
            )
        )                                                               AS detalles;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Índices adicionales
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_cierre_log_periodo_grupo
    ON ades_cierre_periodo_log (periodo_evaluacion_id, grupo_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Triggers de auditoría
-- ─────────────────────────────────────────────────────────────────────────────
SELECT auditoria.asignar_trigger('ades_cierre_periodo_log');

COMMIT;
