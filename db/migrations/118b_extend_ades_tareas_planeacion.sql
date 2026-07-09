-- =============================================================================
-- MIGRACION 118b: Extender ades_tareas — Vincular a Planeación y Aprendizajes
-- =============================================================================
-- Objetivo: Habilitar que cada tarea se vincule a una planeación específica
--           y declare qué aprendizajes esperados evalúa.
-- Fase: 1 (Foundation - Planeaciones Semanales)
-- Fecha: 2026-07-08
-- =============================================================================

BEGIN;

-- =============================================================================
-- 1. ALTER TABLE: ades_tareas
-- =============================================================================

ALTER TABLE ades_tareas
    ADD COLUMN IF NOT EXISTS planeacion_clase_id UUID REFERENCES ades_planeacion_clases(ref),
    ADD COLUMN IF NOT EXISTS aprendizajes_esperados UUID[] DEFAULT '{}';

COMMENT ON COLUMN ades_tareas.planeacion_clase_id IS
'Referencia a la planeación de clase a la que pertenece esta tarea (vinculación FASE 1/2).
Permite trazabilidad: planeación → aprendizajes → tareas → calificaciones.';

COMMENT ON COLUMN ades_tareas.aprendizajes_esperados IS
'Array de UUIDs de aprendizajes_esperados que esta tarea evalúa.
Hereda de la planeación si no se especifica explícitamente.
Permite tracking: qué aprendizaje mide cada tarea.';

-- =============================================================================
-- 2. ÍNDICES PARA NUEVAS COLUMNAS
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_tareas_planeacion_clase_id
    ON ades_tareas(planeacion_clase_id);

COMMENT ON INDEX idx_tareas_planeacion_clase_id IS
'Índice para búsquedas por planeación: SELECT * FROM ades_tareas WHERE planeacion_clase_id = ?';

CREATE INDEX IF NOT EXISTS idx_tareas_aprendizajes_gin
    ON ades_tareas USING GIN(aprendizajes_esperados);

COMMENT ON INDEX idx_tareas_aprendizajes_gin IS
'Índice GIN para búsquedas en array: SELECT * FROM ades_tareas WHERE aprendizajes_esperados && ?';

-- =============================================================================
-- 3. FUNCIÓN: fn_heredar_aprendizajes_desde_planeacion()
-- =============================================================================
-- Automáticamente rellena aprendizajes_esperados si no vienen del cliente.

CREATE OR REPLACE FUNCTION fn_heredar_aprendizajes_desde_planeacion()
RETURNS TRIGGER AS $$
BEGIN
    -- Si la tarea se vincula a una planeación y no tiene aprendizajes explícitos
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

-- Trigger: Al insertar/actualizar tarea
CREATE TRIGGER trg_heredar_aprendizajes
BEFORE INSERT OR UPDATE ON ades_tareas
FOR EACH ROW
EXECUTE FUNCTION fn_heredar_aprendizajes_desde_planeacion();

-- =============================================================================
-- 4. VERIFICACIÓN
-- =============================================================================

SELECT
    'ades_tareas extensión' as tabla,
    COUNT(*) as filas,
    COUNT(DISTINCT planeacion_clase_id) as tareas_con_planeacion,
    COUNT(DISTINCT aprendizajes_esperados) as tareas_con_aprendizajes
FROM ades_tareas
WHERE is_active = TRUE;

COMMIT;

-- =============================================================================
-- NOTAS DE EJECUCIÓN
-- =============================================================================
-- 1. Ejecutar: psql -U ades_admin -d ades < 118b_extend_ades_tareas_planeacion.sql
-- 2. Verificar: SELECT * FROM ades_tareas WHERE planeacion_clase_id IS NOT NULL LIMIT 5;
-- 3. Próxima: Mig 118c (extender ades_calificaciones_evaluaciones)
-- =============================================================================
