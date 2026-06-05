-- Migración 018: Índices de rendimiento adicionales para consultas de libreta y tareas.
-- Optimiza la búsqueda de entregas de una tarea y las calificaciones de un grupo por materia.

CREATE INDEX IF NOT EXISTS idx_ades_tareas_entregas_tarea ON public.ades_tareas_entregas (tarea_id);
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_periodo_grupo_materia ON public.ades_calificaciones_periodo (grupo_id, materia_id);
