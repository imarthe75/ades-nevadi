-- =============================================================================
-- Migración 035: Ampliar ades_aulas + crear ades_disponibilidad_aula (AC-006)
-- La tabla ades_aulas existe desde FASE 3 con estructura mínima.
-- Esta migración agrega capacidad, equipamiento, estado y disponibilidad.
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Ampliar ades_aulas con columnas faltantes
-- ─────────────────────────────────────────────────────────────────────────────

-- Renombrar columna capacidad → capacidad_alumnos para consistencia
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ades_aulas' AND column_name = 'capacidad'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ades_aulas' AND column_name = 'capacidad_alumnos'
    ) THEN
        ALTER TABLE ades_aulas RENAME COLUMN capacidad TO capacidad_alumnos;
    END IF;
END $$;

-- Agregar columnas nuevas (idempotente)
ALTER TABLE ades_aulas
    ADD COLUMN IF NOT EXISTS clave_aula          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS piso                SMALLINT    NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS edificio            VARCHAR(40),
    ADD COLUMN IF NOT EXISTS capacidad_maxima    SMALLINT,
    ADD COLUMN IF NOT EXISTS tiene_proyector     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tiene_pizarra_digital BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tiene_pizarron      BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS tiene_aire_acondicionado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tiene_ventiladores  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS tiene_internet      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS num_computadoras    SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estado_aula         VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    ADD COLUMN IF NOT EXISTS observaciones       TEXT;

-- Actualizar CHECK en tipo_aula: expandir valores permitidos
ALTER TABLE ades_aulas DROP CONSTRAINT IF EXISTS ades_aulas_tipo_aula_check;
ALTER TABLE ades_aulas ADD CONSTRAINT ades_aulas_tipo_aula_check
    CHECK (tipo_aula IN (
        'AULA', 'SALON', 'LABORATORIO', 'COMPUTO', 'TALLER',
        'AUDITORIO', 'BIBLIOTECA', 'GIMNASIO', 'CANCHA',
        'AREA_DEPORTIVA', 'SALA_MAESTROS', 'DIRECCION', 'OTRO'
    ));

-- CHECK estado_aula
DO $$ BEGIN
    ALTER TABLE ades_aulas ADD CONSTRAINT chk_aula_estado
        CHECK (estado_aula IN ('ACTIVA', 'EN_MANTENIMIENTO', 'INHABILITADA', 'FUERA_DE_SERVICIO'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Índices adicionales
CREATE INDEX IF NOT EXISTS idx_aula_plantel_activa ON ades_aulas (plantel_id, is_active);
CREATE INDEX IF NOT EXISTS idx_aula_tipo_estado    ON ades_aulas (tipo_aula, estado_aula);

COMMENT ON TABLE  ades_aulas IS 'Aulas y espacios físicos por plantel. Incluye capacidad, equipamiento y estado operativo.';
COMMENT ON COLUMN ades_aulas.clave_aula   IS 'Clave corta de identificación, ej: A-101, LAB-2';
COMMENT ON COLUMN ades_aulas.estado_aula  IS 'ACTIVA | EN_MANTENIMIENTO | INHABILITADA | FUERA_DE_SERVICIO';
COMMENT ON COLUMN ades_aulas.tipo_aula    IS 'Tipo de espacio: SALON, LABORATORIO, TALLER, AUDITORIO, etc.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Nueva tabla: disponibilidad / asignación de aula
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_disponibilidad_aula (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    aula_id             UUID        NOT NULL REFERENCES ades_aulas(id),
    grupo_id            UUID        REFERENCES ades_grupos(id),
    ciclo_escolar_id    UUID        REFERENCES ades_ciclos_escolares(id),

    dia_semana          SMALLINT    NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_inicio         TIME        NOT NULL,
    hora_fin            TIME        NOT NULL,
    CONSTRAINT chk_disp_aula_horas CHECK (hora_fin > hora_inicio),

    motivo_bloqueo      VARCHAR(80),

    row_version         INT         NOT NULL DEFAULT 1,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER
);

CREATE INDEX IF NOT EXISTS idx_disp_aula_dia ON ades_disponibilidad_aula (aula_id, dia_semana, is_active);
CREATE INDEX IF NOT EXISTS idx_disp_grupo    ON ades_disponibilidad_aula (grupo_id);

COMMENT ON TABLE ades_disponibilidad_aula IS
    'Asignación de grupos a aulas por franja horaria semanal. Permite detectar conflictos de doble uso.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Función: detectar conflicto de aula en franja horaria
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION detectar_conflicto_aula(
    p_aula_id      UUID,
    p_dia_semana   SMALLINT,
    p_hora_inicio  TIME,
    p_hora_fin     TIME,
    p_excluir_id   UUID DEFAULT NULL
) RETURNS TABLE (
    conflicto       BOOLEAN,
    num_conflictos  INT,
    detalle         JSONB
) LANGUAGE plpgsql AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*) > 0,
        COUNT(*)::INT,
        COALESCE(jsonb_agg(jsonb_build_object(
            'id',           da.id,
            'grupo_id',     da.grupo_id,
            'hora_inicio',  da.hora_inicio::text,
            'hora_fin',     da.hora_fin::text,
            'motivo',       da.motivo_bloqueo
        )), '[]'::jsonb)
    FROM ades_disponibilidad_aula da
    WHERE da.aula_id    = p_aula_id
      AND da.dia_semana = p_dia_semana
      AND da.is_active  = TRUE
      AND (p_excluir_id IS NULL OR da.id <> p_excluir_id)
      AND p_hora_inicio < da.hora_fin
      AND p_hora_fin    > da.hora_inicio;
END;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Vista: resumen de ocupación por plantel
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW ades_v_ocupacion_aulas AS
SELECT
    a.id,
    a.plantel_id,
    pl.nombre_plantel,
    a.nombre_aula,
    a.clave_aula,
    a.tipo_aula,
    a.capacidad_alumnos,
    a.estado_aula,
    a.piso,
    a.edificio,
    a.tiene_proyector,
    a.tiene_pizarra_digital,
    a.tiene_internet,
    a.num_computadoras,
    COUNT(da.id)::INT                              AS franjas_ocupadas,
    ROUND(COUNT(da.id)::NUMERIC / 40 * 100, 1)    AS pct_ocupacion
FROM ades_aulas a
JOIN ades_planteles pl ON pl.id = a.plantel_id
LEFT JOIN ades_disponibilidad_aula da
       ON da.aula_id = a.id AND da.is_active = TRUE
WHERE a.is_active = TRUE
GROUP BY a.id, a.plantel_id, pl.nombre_plantel, a.nombre_aula, a.clave_aula,
         a.tipo_aula, a.capacidad_alumnos, a.estado_aula, a.piso, a.edificio,
         a.tiene_proyector, a.tiene_pizarra_digital, a.tiene_internet, a.num_computadoras;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Trigger de auditoría en tabla nueva
-- ─────────────────────────────────────────────────────────────────────────────
SELECT auditoria.asignar_trigger('ades_disponibilidad_aula');

COMMIT;
