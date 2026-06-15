-- ============================================================
-- MIGRACIÓN 057 — INCLUSIÓN DE GÉNERO E IDENTIDAD SOCIAL
-- Alineación con lineamientos SEP / NEM para personas
-- no binarias y uso de nombre social en el entorno escolar.
-- ============================================================
-- Ejecutar:
--   docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/057_inclusion_genero.sql

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 1. Nuevos campos en ades_personas
-- ─────────────────────────────────────────────────────────────
ALTER TABLE ades_personas
    ADD COLUMN IF NOT EXISTS nombre_social              VARCHAR(150),
    ADD COLUMN IF NOT EXISTS genero_autopercibido       VARCHAR(40),
    ADD COLUMN IF NOT EXISTS pronombres                 VARCHAR(40),
    ADD COLUMN IF NOT EXISTS datos_sensibles_restringidos BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ades_personas.nombre_social IS
    'Nombre con el que la persona desea ser llamada en el entorno escolar.
    Si es NULL, los sistemas usarán el nombre legal (campo nombre).
    Según lineamientos SEP/NEM para infancias y adolescencias trans y no binarias.';

COMMENT ON COLUMN ades_personas.genero_autopercibido IS
    'Género autopercibido para uso interno escolar (listas, comunicaciones).
    Puede diferir del género legal registrado en el campo genero.
    Valores: MASCULINO, FEMENINO, NO_BINARIO, OTRO, PREFIERO_NO_DECIR.';

COMMENT ON COLUMN ades_personas.pronombres IS
    'Pronombres preferidos. Ej: él/sus, ella/sus, elle/sus, ellos/sus.
    Opcional; guía al personal docente y administrativo.';

COMMENT ON COLUMN ades_personas.datos_sensibles_restringidos IS
    'TRUE = nombre_social, genero_autopercibido y pronombres solo son visibles
    para personal con nivel_acceso <= 2 (directivos/coordinadores/orientadores).
    Protección de datos conforme a LGPD.';

-- ─────────────────────────────────────────────────────────────
-- 2. Índice parcial para búsquedas por nombre social
-- ─────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_ades_personas_nombre_social
    ON ades_personas (nombre_social)
    WHERE nombre_social IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- 3. Vista auxiliar: nombre_en_pantalla
--    Devuelve nombre_social cuando existe, nombre legal en caso contrario.
--    Usada por asistencias, listas y comunicados internos.
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW v_personas_nombre_pantalla AS
SELECT
    id,
    COALESCE(nombre_social, nombre)     AS nombre_en_pantalla,
    nombre                              AS nombre_legal,
    apellido_paterno,
    apellido_materno,
    curp,
    genero                              AS genero_legal,
    genero_autopercibido,
    pronombres,
    datos_sensibles_restringidos,
    nombre_social IS NOT NULL           AS usa_nombre_social
FROM ades_personas;

COMMENT ON VIEW v_personas_nombre_pantalla IS
    'Vista que expone nombre_en_pantalla = COALESCE(nombre_social, nombre).
    Usar en listas de asistencia, comunicaciones internas y cualquier contexto
    que NO sea emisión de documentos oficiales (boletas/certificados SEP).';

COMMIT;
