-- =============================================================================
-- Migración: 094_dedup_codigos_postales.sql
-- Descripción: Elimina filas duplicadas en ades_codigos_postales generadas por
--              sincronizaciones SEPOMEX repetidas (ON CONFLICT solo en PK UUID,
--              no en (codigo_postal, localidad_id)). Redirige FKs en
--              ades_direcciones a la fila canónica (uuid más antiguo), borra
--              duplicados y agrega constraint UNIQUE (codigo_postal, localidad_id)
--              para evitar reincidencia.
-- Tablas afectadas: ades_codigos_postales, ades_direcciones
-- Dependencias: ades_localidades, ades_direcciones (FK a ades_codigos_postales)
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- 093_dedup_codigos_postales.sql
-- Elimina duplicados en ades_codigos_postales generados por sincronizaciones SEPOMEX
-- repetidas sin constraint UNIQUE. Añade el constraint para evitar futuros duplicados.
--
-- Causa: ON CONFLICT DO NOTHING en el task sepomex.py conflictúa sólo en PK (uuid),
-- nunca en (codigo_postal, localidad_id), por lo que cada sync insertaba filas nuevas.
-- Resultado: ~316k filas en lugar de ~158k colonias reales.
--
-- Estrategia:
--  1. Por cada grupo (codigo_postal, localidad_id) elegir la fila canónica (id más bajo = más antigua).
--  2. Redirigir ades_direcciones.codigo_postal_id desde filas duplicadas → fila canónica.
--  3. Eliminar filas duplicadas.
--  4. Reemplazar el índice no-único por un UNIQUE CONSTRAINT.

BEGIN;

-- ── 1. Tabla temporal con el ID canónico por (cp, localidad) ────────────────
CREATE TEMP TABLE _cp_canon AS
SELECT DISTINCT ON (codigo_postal, localidad_id)
       id           AS canon_id,
       codigo_postal,
       localidad_id
FROM   ades_codigos_postales
ORDER  BY codigo_postal, localidad_id, id ASC;   -- id ASC = uuid más antiguo

-- ── 2. Redirigir FKs en ades_direcciones que apuntan a duplicados ────────────
UPDATE ades_direcciones d
SET    codigo_postal_id = c.canon_id
FROM   _cp_canon c
WHERE  d.codigo_postal_id IN (
         SELECT cp.id
         FROM   ades_codigos_postales cp
         WHERE  cp.codigo_postal = c.codigo_postal
           AND  cp.localidad_id  = c.localidad_id
           AND  cp.id            <> c.canon_id
       )
  AND  d.codigo_postal_id <> c.canon_id;

-- ── 3. Borrar filas duplicadas (no canónicas) ────────────────────────────────
DELETE FROM ades_codigos_postales cp
USING  _cp_canon c
WHERE  cp.codigo_postal = c.codigo_postal
  AND  cp.localidad_id  = c.localidad_id
  AND  cp.id            <> c.canon_id;

DROP TABLE _cp_canon;

-- ── 4. Reemplazar índice compuesto por UNIQUE CONSTRAINT ─────────────────────
DROP INDEX IF EXISTS idx_codpos_cp_localidad;
DROP INDEX IF EXISTS idx_ades_cp_localidad_id;

ALTER TABLE ades_codigos_postales
    ADD CONSTRAINT uq_cp_localidad UNIQUE (codigo_postal, localidad_id);

-- El índice anterior era idx_cp_municipio (duplicado de idx_codpos_municipio), limpiar también
DROP INDEX IF EXISTS idx_cp_municipio;

-- ── Verificación ─────────────────────────────────────────────────────────────
DO $$
DECLARE
  total   BIGINT;
  unicos  BIGINT;
BEGIN
  SELECT COUNT(*),
         COUNT(DISTINCT codigo_postal || '|' || localidad_id::text)
  INTO   total, unicos
  FROM   ades_codigos_postales;

  IF total <> unicos THEN
    RAISE EXCEPTION 'Aún hay duplicados: % filas, % únicas', total, unicos;
  END IF;
  RAISE NOTICE 'OK: % colonias sin duplicados', total;
END $$;

COMMIT;
