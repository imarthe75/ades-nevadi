-- =============================================================================
-- 131_indices_unicos_matviews_bi.sql
-- Índices únicos faltantes en ades_bi.mv_resumen_plantel y
-- ades_bi.mv_calificaciones_grupo — REFRESH MATERIALIZED VIEW CONCURRENTLY
-- (usado por el job horario de celery-beat, refresh_vistas_materializadas)
-- exige un índice único sobre la vista o falla con
-- "cannot refresh materialized view concurrently" / ObjectNotInPrerequisiteState.
-- mv_riesgo_academico ya tenía el suyo (idx_mv_riesgo_academico); estas dos no.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_resumen_plantel
    ON ades_bi.mv_resumen_plantel (plantel_id, nombre_nivel);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_calificaciones_grupo
    ON ades_bi.mv_calificaciones_grupo (grupo_id, materia_id, numero_periodo);
