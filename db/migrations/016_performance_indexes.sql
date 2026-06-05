-- Migration 016: Adición de índices de rendimiento para optimización de consultas en calificaciones y tareas.
-- Autor: QA Agent
-- Fecha: 2026-06-05

-- Índices para optimizar búsquedas y cruces por grupo_id y estudiante_id en inscripciones
CREATE INDEX IF NOT EXISTS idx_ades_inscripciones_grupo ON public.ades_inscripciones (grupo_id);
CREATE INDEX IF NOT EXISTS idx_ades_inscripciones_estudiante ON public.ades_inscripciones (estudiante_id);

-- Índices para optimizar búsquedas de entregas de tareas por estudiante
CREATE INDEX IF NOT EXISTS idx_ades_tareas_entregas_estudiante ON public.ades_tareas_entregas (estudiante_id);

-- Índices para optimizar las consultas de la libreta de calificaciones por grupo y estudiante
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_periodo_grupo ON public.ades_calificaciones_periodo (grupo_id);
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_periodo_estudiante ON public.ades_calificaciones_periodo (estudiante_id);
