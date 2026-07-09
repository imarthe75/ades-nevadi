-- =============================================================================
-- MIGRACION 118c: Extender ades_calificaciones_evaluaciones
-- =============================================================================
-- Objetivo: Habilitar vinculación de exámenes/evaluaciones a planeación
--           y especificar qué aprendizajes evalúa cada uno.
-- Fase: 1 (Foundation - Planeaciones Semanales)
-- Fecha: 2026-07-08
-- =============================================================================

BEGIN;

-- =============================================================================
-- 1. ALTER TABLE: ades_calificaciones_evaluaciones
-- =============================================================================

ALTER TABLE ades_calificaciones_evaluaciones
    ADD COLUMN IF NOT EXISTS planeacion_clase_id UUID REFERENCES ades_planeacion_clases(ref),
    ADD COLUMN IF NOT EXISTS aprendizajes_esperados UUID[] DEFAULT '{}';

COMMENT ON COLUMN ades_calificaciones_evaluaciones.planeacion_clase_id IS
'Referencia a la planeación de clase a la que pertenece esta evaluación.
Permite trazabilidad: planeación → aprendizajes → evaluaciones → calificaciones.';

COMMENT ON COLUMN ades_calificaciones_evaluaciones.aprendizajes_esperados IS
'Array de UUIDs de aprendizajes_esperados que esta evaluación mide.
Hereda de la planeación si se vincula a evaluación con planeacion_clase_id.';

-- =============================================================================
-- 2. ÍNDICES PARA NUEVAS COLUMNAS
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_calificaciones_eval_planeacion_clase_id
    ON ades_calificaciones_evaluaciones(planeacion_clase_id);

COMMENT ON INDEX idx_calificaciones_eval_planeacion_clase_id IS
'Índice para búsquedas por planeación: SELECT * FROM ades_calificaciones_evaluaciones WHERE planeacion_clase_id = ?';

CREATE INDEX IF NOT EXISTS idx_calificaciones_eval_aprendizajes_gin
    ON ades_calificaciones_evaluaciones USING GIN(aprendizajes_esperados);

COMMENT ON INDEX idx_calificaciones_eval_aprendizajes_gin IS
'Índice GIN para búsquedas en array: SELECT * FROM ades_calificaciones_evaluaciones WHERE aprendizajes_esperados && ?';

-- =============================================================================
-- 3. FUNCIÓN: fn_heredar_aprendizajes_eval_desde_planeacion()
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_heredar_aprendizajes_eval_desde_planeacion()
RETURNS TRIGGER AS $$
BEGIN
    -- Si la calificación se vincula a una planeación y no tiene aprendizajes explícitos
    IF NEW.planeacion_clase_id IS NOT NULL AND
       (NEW.aprendizajes_esperados IS NULL OR array_length(NEW.aprendizajes_esperados, 1) = 0)
    THEN
        -- Heredar aprendizajes de la planeación
        SELECT ARRAY_AGG(aprendizaje_esperado_id)
        INTO NEW.aprendizajes_esperados
        FROM ades_planeacion_aprendizajes
        WHERE planeacion_clase_id = NEW.planeacion_clase_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Al insertar/actualizar calificación de evaluación
CREATE TRIGGER trg_heredar_aprendizajes_eval
BEFORE INSERT OR UPDATE ON ades_calificaciones_evaluaciones
FOR EACH ROW
EXECUTE FUNCTION fn_heredar_aprendizajes_eval_desde_planeacion();

-- =============================================================================
-- 4. VERIFICACIÓN
-- =============================================================================

SELECT
    'ades_calificaciones_evaluaciones extensión' as tabla,
    COUNT(*) as filas,
    COUNT(DISTINCT planeacion_clase_id) as evals_con_planeacion,
    COUNT(DISTINCT aprendizajes_esperados) as evals_con_aprendizajes
FROM ades_calificaciones_evaluaciones
WHERE is_active = TRUE;

COMMIT;

-- =============================================================================
-- NOTAS DE EJECUCIÓN
-- =============================================================================
-- 1. Ejecutar: psql -U ades_admin -d ades < 118c_extend_ades_evaluaciones_planeacion.sql
-- 2. Verificar: SELECT * FROM ades_calificaciones_evaluaciones WHERE planeacion_clase_id IS NOT NULL LIMIT 5;
-- 3. Próxima: Mig 118d (auditoría y cierre FASE 1)
-- =============================================================================
