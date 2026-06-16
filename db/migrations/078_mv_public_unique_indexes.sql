-- 078_mv_public_unique_indexes.sql
-- Agrega índices únicos a las MVs del schema public para permitir
-- REFRESH MATERIALIZED VIEW CONCURRENTLY (sin bloquear lecturas).

CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_v_asistencias_resumen
    ON public.v_asistencias_resumen (estudiante_id, estatus_asistencia);

CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_v_tareas_entregas_resumen
    ON public.v_tareas_entregas_resumen (estudiante_id);

-- Refresco inicial (sin CONCURRENTLY porque las vistas pueden estar vacías)
REFRESH MATERIALIZED VIEW public.v_asistencias_resumen;
REFRESH MATERIALIZED VIEW public.v_tareas_entregas_resumen;
