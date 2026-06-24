-- =============================================================================
-- Migración: 083_ciclo_sistema_educativo.sql
-- Descripción: Agrega columna sistema_educativo (SEP|UAEMEX) a ades_ciclos_escolares,
--              garantiza un único ciclo vigente por sistema educativo mediante índice
--              parcial y trigger fn_ciclo_sistema_vigente. Corrige estado inconsistente
--              UAEMEX dejando vigente solo el ciclo 26B.
-- Tablas afectadas: ades_ciclos_escolares
-- Dependencias: ades_niveles_educativos, función uuidv7()
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- =============================================================================
-- 083_ciclo_sistema_educativo.sql
-- -----------------------------------------------------------------------------
-- Regla de negocio: por cada SISTEMA EDUCATIVO debe haber un solo "año" (ciclo)
-- vigente a la vez. Resultado operativo: un ciclo SEP (que abarca primaria +
-- secundaria, mismo nombre_ciclo) y un ciclo UAEMEX (preparatoria) vigentes.
--
-- Estrategia (Camino A — sin fusionar filas):
--   * Se CONSERVA ades_ciclos_escolares.nivel_educativo_id (lo usa el modelo de
--     periodos de evaluación como discriminador: primaria 3 periodos vs
--     secundaria 6). Fusionar las filas SEP rompería uq_ades_periodos_eval.
--   * Se agrega sistema_educativo (SEP|UAEMEX), derivado del nivel, para filtrar
--     sin JOIN y para expresar la regla.
--   * La regla se hace cumplir con: (a) índice único parcial = máx. 1 vigente
--     por nivel; (b) trigger = los ciclos vigentes de un mismo sistema deben
--     compartir nombre_ciclo (un solo año vigente por sistema).
--
-- Corrige además un estado inconsistente actual: UAEMEX tenía 27A y 26B ambos
-- vigentes; se deja vigente únicamente 26B (decisión del responsable 2026-06-22).
--
-- Idempotente y transaccional. No elimina columnas ni vistas.
-- =============================================================================

BEGIN;

-- 1) Columna discriminadora -------------------------------------------------
ALTER TABLE ades_ciclos_escolares
    ADD COLUMN IF NOT EXISTS sistema_educativo VARCHAR(10);

-- 2) Backfill desde la autoridad del nivel ----------------------------------
UPDATE ades_ciclos_escolares c
SET    sistema_educativo = n.autoridad_educativa
FROM   ades_niveles_educativos n
WHERE  n.id = c.nivel_educativo_id
  AND  c.sistema_educativo IS DISTINCT FROM n.autoridad_educativa;

ALTER TABLE ades_ciclos_escolares
    ALTER COLUMN sistema_educativo SET NOT NULL;

ALTER TABLE ades_ciclos_escolares
    DROP CONSTRAINT IF EXISTS ck_ciclos_sistema;
ALTER TABLE ades_ciclos_escolares
    ADD  CONSTRAINT ck_ciclos_sistema CHECK (sistema_educativo IN ('SEP','UAEMEX'));

COMMENT ON COLUMN ades_ciclos_escolares.sistema_educativo IS
    'Autoridad del ciclo (SEP|UAEMEX), derivada del nivel por trigger. Regla: un solo año vigente por sistema => 1 ciclo SEP (primaria+secundaria) + 1 ciclo UAEMEX.';

-- 3) Sanear estado actual: un solo año vigente por sistema UAEMEX = 26B ------
UPDATE ades_ciclos_escolares c
SET    es_vigente = FALSE
FROM   ades_niveles_educativos n
WHERE  n.id = c.nivel_educativo_id
  AND  n.autoridad_educativa = 'UAEMEX'
  AND  c.es_vigente = TRUE
  AND  c.nombre_ciclo <> '26B';

-- 4) Regla parte 1: máximo un ciclo vigente por nivel -----------------------
--    (nivel -> sistema es fijo, así que esto acota los vigentes por sistema).
CREATE UNIQUE INDEX IF NOT EXISTS uq_ciclo_vigente_por_nivel
    ON ades_ciclos_escolares (nivel_educativo_id)
    WHERE es_vigente AND is_active;

-- 5) Regla parte 2 + consistencia: trigger ----------------------------------
--    - Deriva sistema_educativo desde el nivel (fuente de verdad).
--    - Si la fila queda vigente, ningún otro ciclo vigente del mismo sistema
--      puede tener un nombre_ciclo distinto (un solo año vigente por sistema).
CREATE OR REPLACE FUNCTION fn_ciclo_sistema_vigente()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_autoridad TEXT;
    v_conflicto TEXT;
BEGIN
    SELECT autoridad_educativa
      INTO v_autoridad
      FROM ades_niveles_educativos
     WHERE id = NEW.nivel_educativo_id;

    IF v_autoridad IS NULL THEN
        RAISE EXCEPTION 'Nivel educativo % inexistente para el ciclo %',
            NEW.nivel_educativo_id, NEW.nombre_ciclo;
    END IF;

    NEW.sistema_educativo := v_autoridad;

    IF NEW.es_vigente AND COALESCE(NEW.is_active, TRUE) THEN
        SELECT c.nombre_ciclo
          INTO v_conflicto
          FROM ades_ciclos_escolares c
          JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
         WHERE n.autoridad_educativa = v_autoridad
           AND c.es_vigente
           AND c.is_active
           AND c.id <> NEW.id
           AND c.nombre_ciclo <> NEW.nombre_ciclo
         LIMIT 1;

        IF v_conflicto IS NOT NULL THEN
            RAISE EXCEPTION
                'El sistema % ya tiene el ciclo vigente "%"; no puede coexistir con "%". Solo se permite un ciclo (año) vigente por sistema.',
                v_autoridad, v_conflicto, NEW.nombre_ciclo;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_ciclo_sistema_vigente ON ades_ciclos_escolares;
CREATE TRIGGER trg_ciclo_sistema_vigente
    BEFORE INSERT OR UPDATE ON ades_ciclos_escolares
    FOR EACH ROW
    EXECUTE FUNCTION fn_ciclo_sistema_vigente();

COMMIT;

-- =============================================================================
-- Verificación rápida (no transaccional):
--   SELECT sistema_educativo, COUNT(*) FILTER (WHERE es_vigente AND is_active)
--   FROM ades_ciclos_escolares GROUP BY sistema_educativo;   -- esperado: SEP=2, UAEMEX=1
-- =============================================================================
