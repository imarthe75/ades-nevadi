-- 054_fix_sepomex_dedup.sql
-- Elimina 475,167 filas duplicadas en ades_codigos_postales.
--
-- Diagnóstico: el catálogo SEPOMEX fue importado 4 veces sin deduplicar.
-- Antes: 633,255 filas → 158,088 pares únicos (codigo_postal, localidad_id)
-- Después: 158,088 filas reales
--
-- Las 3 filas existentes en ades_direcciones.codigo_postal_id se migran al
-- registro canónico (menor UUID) del mismo par (codigo_postal, localidad_id)
-- antes de borrar los duplicados.
--
-- Modelo de datos resultante:
--   ades_localidades  (152,905): nombres de asentamientos únicos por nombre
--   ades_codigos_postales (158,088): un registro por par (nombre, CP) real SEPOMEX
--   Un mismo nombre puede aparecer en 2+ CPs (ej: "Florida" en CDMX y en Toluca)
--   → esto es correcto en SEPOMEX: son asentamientos distintos con el mismo nombre

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 1: Tabla temporal con los IDs canónicos por par único
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TEMP TABLE tmp_cp_canonicos AS
SELECT DISTINCT ON (codigo_postal, localidad_id)
    id AS id_canonico,
    codigo_postal,
    localidad_id
FROM ades_codigos_postales
ORDER BY codigo_postal, localidad_id, id;   -- menor UUID = canónico

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 2: Reparar ades_direcciones.codigo_postal_id que apuntan a duplicados
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE ades_direcciones d
SET codigo_postal_id = c.id_canonico
FROM ades_codigos_postales old_cp
JOIN tmp_cp_canonicos c
    ON c.codigo_postal = old_cp.codigo_postal
   AND c.localidad_id  = old_cp.localidad_id
WHERE old_cp.id = d.codigo_postal_id
  AND d.codigo_postal_id != c.id_canonico;  -- solo las que apuntan a duplicados

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 3: Eliminar filas duplicadas (conserva solo id_canonico por par)
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM ades_codigos_postales cp
WHERE cp.id NOT IN (SELECT id_canonico FROM tmp_cp_canonicos);

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 4: Índice compuesto para garantizar unicidad en adelante
-- ─────────────────────────────────────────────────────────────────────────────
CREATE UNIQUE INDEX IF NOT EXISTS ux_ades_cp_cp_localidad
    ON ades_codigos_postales (codigo_postal, localidad_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 5: Índice en localidades para el JOIN inverso (perf del buscar endpoint)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_ades_cp_localidad_id
    ON ades_codigos_postales (localidad_id);

DROP TABLE tmp_cp_canonicos;

COMMIT;

-- Verificación post-migración:
-- SELECT COUNT(*), COUNT(DISTINCT (codigo_postal, localidad_id)) FROM ades_codigos_postales;
-- Ambos deben ser iguales (~158,088).
