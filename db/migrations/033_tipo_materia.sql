-- =============================================================================
-- Migración 033: Taxonomía de Materias SEP / UAEMEX / Nevadi
-- =============================================================================
-- Agrega: tipo_materia, reporta_a_sep_uaemex, incluir_en_boleta,
--         codigo_sep, ponderacion_default a ades_materias.
-- Backfill: PRIMARIA/SECUNDARIA → OFICIAL_SEP_*
--           PREPARATORIA        → OFICIAL_UAEMEX_PREP
-- Crea: vistas ades_v_materias_oficiales, ades_v_materias_nevadi
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Nuevas columnas
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE ades_materias
    ADD COLUMN IF NOT EXISTS tipo_materia        VARCHAR(50)    NOT NULL DEFAULT 'OFICIAL_SEP_PRIMARIA',
    ADD COLUMN IF NOT EXISTS reporta_a_sep_uaemex BOOLEAN       NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS incluir_en_boleta   BOOLEAN        NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS codigo_sep          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ponderacion_default NUMERIC(5,4);  -- ej: 0.3333

COMMENT ON COLUMN ades_materias.tipo_materia IS
    'OFICIAL_SEP_PRIMARIA | OFICIAL_SEP_SECUNDARIA | OFICIAL_UAEMEX_PREP | NEVADI_FORMATIVA | NEVADI_ENRIQUECIMIENTO | NEVADI_ESPECIALIZADA';
COMMENT ON COLUMN ades_materias.reporta_a_sep_uaemex IS
    'TRUE = aparece en reportes oficiales SEP/UAEMEX. FALSE = solo uso interno Nevadi.';
COMMENT ON COLUMN ades_materias.incluir_en_boleta IS
    'TRUE = se muestra en la boleta. Las materias Nevadi pueden omitirse.';

ALTER TABLE ades_materias
    ADD CONSTRAINT chk_tipo_materia CHECK (tipo_materia IN (
        'OFICIAL_SEP_PRIMARIA', 'OFICIAL_SEP_SECUNDARIA', 'OFICIAL_UAEMEX_PREP',
        'NEVADI_FORMATIVA', 'NEVADI_ENRIQUECIMIENTO', 'NEVADI_ESPECIALIZADA'
    ));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Backfill según nivel educativo
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ades_materias m
   SET tipo_materia         = 'OFICIAL_SEP_PRIMARIA',
       reporta_a_sep_uaemex = TRUE,
       incluir_en_boleta    = TRUE
  FROM ades_niveles_educativos ne
 WHERE ne.id = m.nivel_educativo_id
   AND ne.nombre_nivel = 'PRIMARIA';

UPDATE ades_materias m
   SET tipo_materia         = 'OFICIAL_SEP_SECUNDARIA',
       reporta_a_sep_uaemex = TRUE,
       incluir_en_boleta    = TRUE
  FROM ades_niveles_educativos ne
 WHERE ne.id = m.nivel_educativo_id
   AND ne.nombre_nivel = 'SECUNDARIA';

UPDATE ades_materias m
   SET tipo_materia         = 'OFICIAL_UAEMEX_PREP',
       reporta_a_sep_uaemex = TRUE,
       incluir_en_boleta    = TRUE
  FROM ades_niveles_educativos ne
 WHERE ne.id = m.nivel_educativo_id
   AND ne.nombre_nivel = 'PREPARATORIA';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Índice para filtrar por tipo
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_materias_tipo
    ON ades_materias (tipo_materia) WHERE is_active = TRUE;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Vistas
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW ades_v_materias_oficiales AS
SELECT m.*, ne.nombre_nivel
  FROM ades_materias m
  JOIN ades_niveles_educativos ne ON ne.id = m.nivel_educativo_id
 WHERE m.tipo_materia IN ('OFICIAL_SEP_PRIMARIA', 'OFICIAL_SEP_SECUNDARIA', 'OFICIAL_UAEMEX_PREP')
   AND m.is_active = TRUE;

COMMENT ON VIEW ades_v_materias_oficiales IS
    'Materias oficiales SEP/UAEMEX que reportan a autoridades educativas.';

CREATE OR REPLACE VIEW ades_v_materias_nevadi AS
SELECT m.*, ne.nombre_nivel
  FROM ades_materias m
  JOIN ades_niveles_educativos ne ON ne.id = m.nivel_educativo_id
 WHERE m.tipo_materia LIKE 'NEVADI_%'
   AND m.is_active = TRUE;

COMMENT ON VIEW ades_v_materias_nevadi IS
    'Materias propias del Instituto Nevadi (formativas, enriquecimiento, especializadas).';

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Quitar DEFAULT artificial ahora que el backfill está hecho
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE ades_materias ALTER COLUMN tipo_materia DROP DEFAULT;

COMMIT;
