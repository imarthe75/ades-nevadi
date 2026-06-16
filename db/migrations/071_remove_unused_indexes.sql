-- =============================================================================
-- SPRINT 3 FASE 1: Eliminar Índices No Usados
-- =============================================================================
-- Esta migración elimina 20 índices que tienen 0 scans desde su creación
-- Liberará 79 MB de espacio y mejorará INSERT/UPDATE performance
-- Estos índices fueron identificados en SPRINT 2 (db/analysis/INDEX_RECOMMENDATIONS.md)

-- Antes de ejecutar: Validar que estos índices realmente no se usan en aplicación

\echo '=== ELIMINANDO ÍNDICES NO USADOS (79 MB) ==='

-- 1. ades_asistencias_ref_key (29 MB, 0 scans)
DROP INDEX IF EXISTS ades_asistencias_ref_key CASCADE;
\echo '✓ ades_asistencias_ref_key eliminado (29 MB)'

-- 2. ux_ades_cp_cp_localidad (25 MB, 0 scans)
DROP INDEX IF EXISTS ux_ades_cp_cp_localidad CASCADE;
\echo '✓ ux_ades_cp_cp_localidad eliminado (25 MB)'

-- 3. uq_ades_cal_periodo (14 MB, 0 scans)
DROP INDEX IF EXISTS uq_ades_cal_periodo CASCADE;
\echo '✓ uq_ades_cal_periodo eliminado (14 MB)'

-- 4. uq_ades_entregas (11 MB, 0 scans)
DROP INDEX IF EXISTS uq_ades_entregas CASCADE;
\echo '✓ uq_ades_entregas eliminado (11 MB)'

-- 5. ades_calificaciones_periodo_ref_key (8.6 MB, 0 scans)
DROP INDEX IF EXISTS ades_calificaciones_periodo_ref_key CASCADE;
\echo '✓ ades_calificaciones_periodo_ref_key eliminado (8.6 MB)'

-- 6. idx_entregas_tarea (8.4 MB, 0 scans)
DROP INDEX IF EXISTS idx_entregas_tarea CASCADE;
\echo '✓ idx_entregas_tarea eliminado (8.4 MB)'

-- 7. ades_tareas_entregas_ref_key (7.2 MB, 0 scans)
DROP INDEX IF EXISTS ades_tareas_entregas_ref_key CASCADE;
\echo '✓ ades_tareas_entregas_ref_key eliminado (7.2 MB)'

-- 8. idx_ades_cal_periodo_est (5.4 MB, 0 scans)
DROP INDEX IF EXISTS idx_ades_cal_periodo_est CASCADE;
\echo '✓ idx_ades_cal_periodo_est eliminado (5.4 MB)'

-- 9. idx_ades_personas_nombre (2.8 MB, 0 scans)
DROP INDEX IF EXISTS idx_ades_personas_nombre CASCADE;
\echo '✓ idx_ades_personas_nombre eliminado (2.8 MB)'

-- 10-20: Índices adicionales sin uso
DROP INDEX IF EXISTS idx_cal_periodo_cerrada CASCADE;
DROP INDEX IF EXISTS idx_ades_calificaciones_periodo_grupo CASCADE;
DROP INDEX IF EXISTS idx_cal_periodo_grupo_periodo CASCADE;
DROP INDEX IF EXISTS idx_asist_clase CASCADE;
DROP INDEX IF EXISTS idx_asistencias_justificacion CASCADE;
DROP INDEX IF EXISTS idx_ades_tareas_entregas_estudiante CASCADE;
DROP INDEX IF EXISTS idx_cal_grupo_mat CASCADE;
DROP INDEX IF EXISTS idx_cp_estado CASCADE;
DROP INDEX IF EXISTS ades_clases_ref_key CASCADE;
DROP INDEX IF EXISTS idx_cal_per_cerrado_por CASCADE;
DROP INDEX IF EXISTS ades_tareas_ref_key CASCADE;

\echo '✓ 11 índices adicionales eliminados'

-- Verificación post-eliminación
\echo ''
\echo '=== VERIFICACIÓN: ÍNDICES ELIMINADOS EXITOSAMENTE ==='
SELECT COUNT(*) as remaining_indexes FROM pg_indexes WHERE schemaname = 'public';

