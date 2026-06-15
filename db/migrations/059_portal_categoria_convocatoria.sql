-- ============================================================
-- MIGRACIÓN 059 — CATEGORÍA DE CONVOCATORIAS
-- Diferencia convocatorias de RECURSOS_HUMANOS vs OFERTA_EDUCATIVA
-- para el portal externo portalnvd.setag.mx
-- ============================================================
-- Ejecutar:
--   docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/059_portal_categoria_convocatoria.sql

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 1. Nuevo tipo enumerado de categoría
-- ─────────────────────────────────────────────────────────────
DO $$ BEGIN
  CREATE TYPE portal.categoria_convocatoria AS ENUM (
    'RECURSOS_HUMANOS',
    'OFERTA_EDUCATIVA'
  );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

COMMENT ON TYPE portal.categoria_convocatoria IS
  'Clasificación de alto nivel: RECURSOS_HUMANOS (vacantes docentes/admin)
   vs OFERTA_EDUCATIVA (inscripción, reinscripción, becas, intercambio, extracurricular).';

-- ─────────────────────────────────────────────────────────────
-- 2. Agregar columna categoria a portal.convocatorias
-- ─────────────────────────────────────────────────────────────
ALTER TABLE portal.convocatorias
  ADD COLUMN IF NOT EXISTS categoria portal.categoria_convocatoria;

COMMENT ON COLUMN portal.convocatorias.categoria IS
  'RECURSOS_HUMANOS: vacantes de personal. OFERTA_EDUCATIVA: inscripción/becas/intercambio.';

-- ─────────────────────────────────────────────────────────────
-- 3. Poblar registros existentes basándose en el tipo
-- ─────────────────────────────────────────────────────────────
UPDATE portal.convocatorias
SET categoria = CASE
  WHEN tipo IN ('VACANTE_DOCENTE', 'VACANTE_ADMINISTRATIVA') THEN 'RECURSOS_HUMANOS'::portal.categoria_convocatoria
  ELSE 'OFERTA_EDUCATIVA'::portal.categoria_convocatoria
END
WHERE categoria IS NULL;

-- ─────────────────────────────────────────────────────────────
-- 4. Constraint NOT NULL ahora que todos los rows tienen valor
-- ─────────────────────────────────────────────────────────────
ALTER TABLE portal.convocatorias
  ALTER COLUMN categoria SET NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- 5. Constraint que garantiza coherencia tipo <-> categoria
-- ─────────────────────────────────────────────────────────────
ALTER TABLE portal.convocatorias
  DROP CONSTRAINT IF EXISTS ck_categoria_tipo_coherente;

ALTER TABLE portal.convocatorias
  ADD CONSTRAINT ck_categoria_tipo_coherente CHECK (
    (categoria = 'RECURSOS_HUMANOS'  AND tipo IN ('VACANTE_DOCENTE', 'VACANTE_ADMINISTRATIVA')) OR
    (categoria = 'OFERTA_EDUCATIVA'  AND tipo IN ('INSCRIPCION', 'REINSCRIPCION', 'BECA', 'INTERCAMBIO', 'EXTRACURRICULAR'))
  );

-- ─────────────────────────────────────────────────────────────
-- 6. Función auxiliar para inferir categoría desde tipo
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION portal.inferir_categoria(p_tipo portal.tipo_convocatoria)
RETURNS portal.categoria_convocatoria
LANGUAGE sql IMMUTABLE
AS $$
  SELECT CASE
    WHEN p_tipo IN ('VACANTE_DOCENTE', 'VACANTE_ADMINISTRATIVA')
      THEN 'RECURSOS_HUMANOS'::portal.categoria_convocatoria
    ELSE
      'OFERTA_EDUCATIVA'::portal.categoria_convocatoria
  END;
$$;

COMMENT ON FUNCTION portal.inferir_categoria IS
  'Devuelve la categoría correspondiente a un tipo de convocatoria.
   Usar en INSERTs cuando el frontend no envía categoria explícitamente.';

-- ─────────────────────────────────────────────────────────────
-- 7. Actualizar generar_folio para incluir prefijos por categoria
-- ─────────────────────────────────────────────────────────────
-- La función actual ya maneja los tipos individualmente — sin cambios.
-- Los prefijos VACD/VACA ya identifican RRHH; INSC/REIN/BECA/INTC/EXTC identifican OFERTA.

-- ─────────────────────────────────────────────────────────────
-- 8. Índice por categoria para filtros en el portal público
-- ─────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_convocatorias_categoria
  ON portal.convocatorias(categoria)
  WHERE is_active = TRUE AND is_published = TRUE;

CREATE INDEX IF NOT EXISTS idx_convocatorias_categoria_tipo
  ON portal.convocatorias(categoria, tipo)
  WHERE is_active = TRUE;

COMMIT;
