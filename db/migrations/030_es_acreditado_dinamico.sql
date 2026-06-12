-- Migración 030 — es_acreditado dinámico por nivel educativo
-- Problema: la columna GENERATED siempre usaba calificacion_final >= 6.0
--           incorrecto para UAEMEX/PREPARATORIA cuyo mínimo es 60.0
-- Solución: columna regular + trigger BEFORE INSERT/UPDATE que resuelve el
--           minimo_aprobatorio del nivel via grupo → grado → nivel_educativo

BEGIN;

-- 1. Eliminar la columna GENERATED y reemplazar por columna regular
ALTER TABLE ades_calificaciones_periodo DROP COLUMN es_acreditado;
ALTER TABLE ades_calificaciones_periodo
    ADD COLUMN es_acreditado BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Trigger function que resuelve el umbral dinámicamente
CREATE OR REPLACE FUNCTION trg_set_es_acreditado()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_minimo NUMERIC(5,1);
BEGIN
    SELECT ne.minimo_aprobatorio
      INTO v_minimo
      FROM ades_grupos      g
      JOIN ades_grados       gr ON gr.id  = g.grado_id
      JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
     WHERE g.id = NEW.grupo_id;

    -- Si no se encuentra el nivel (datos inconsistentes), usar 6.0 SEP por defecto
    NEW.es_acreditado := NEW.calificacion_final >= COALESCE(v_minimo, 6.0);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_calificacion_periodo_acreditado
    BEFORE INSERT OR UPDATE OF calificacion_final, grupo_id
    ON ades_calificaciones_periodo
    FOR EACH ROW EXECUTE FUNCTION trg_set_es_acreditado();

-- 3. Backfill de registros existentes
UPDATE ades_calificaciones_periodo cp
   SET es_acreditado = cp.calificacion_final >= COALESCE(
       (SELECT ne.minimo_aprobatorio
          FROM ades_grupos      g
          JOIN ades_grados       gr ON gr.id  = g.grado_id
          JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
         WHERE g.id = cp.grupo_id),
       6.0
   );

-- 4. Auditoría
SELECT auditoria.asignar_trigger('ades_calificaciones_periodo');

COMMIT;
