-- =============================================================================
-- 158_check_calificacion_obtenida_entregas.sql
-- Hallazgo H-2 de auditoría externa (2026-07-20), verificado real: la migración
-- 133 puso un CHECK 0-10 en ades_calificaciones_tareas.calificacion, pero esa
-- tabla nunca recibe escrituras desde el código Java (confirmado por grep — cero
-- INSERT/UPDATE). La columna que SÍ escriben todos los flujos de calificación
-- (EntregaPersistenceAdapter, ActividadesWriteService, TareaPersistenceAdapter)
-- es ades_tareas_entregas.calificacion_obtenida, y esa nunca tuvo protección de
-- rango a nivel de BD — solo validación en Java. Defensa en profundidad.
-- Rango verificado en vivo: escala real observada 3.30-10.00 (numeric(5,2)),
-- consistente con escala NEM 0-10; NEM Fase 3 cualitativa (A/B/C/D, 1°-2° de
-- primaria) se almacena en una representación separada, no en esta columna.
-- =============================================================================

BEGIN;

ALTER TABLE ades_tareas_entregas
    ADD CONSTRAINT chk_calificacion_obtenida_rango
    CHECK (calificacion_obtenida IS NULL OR (calificacion_obtenida >= 0 AND calificacion_obtenida <= 10));

COMMIT;
