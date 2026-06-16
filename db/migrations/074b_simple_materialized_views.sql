-- =============================================================================
-- SPRINT 3 FASE 5B: Crear Materialized Views Simplificadas
-- =============================================================================

\echo '=== CREANDO MATERIALIZED VIEWS SIMPLIFICADAS ==='

-- 1. Vista materializada simple: Resumen de estudiantes
CREATE MATERIALIZED VIEW IF NOT EXISTS v_estudiantes_resumen AS
SELECT 
  e.id,
  e.estado,
  COUNT(*) as total_estudiantes
FROM ades_estudiantes e
GROUP BY e.id, e.estado;

\echo '✓ v_estudiantes_resumen'

-- 2. Vista materializada: Conteo de asistencias
CREATE MATERIALIZED VIEW IF NOT EXISTS v_asistencias_resumen AS
SELECT 
  a.estudiante_id,
  a.estatus_asistencia,
  COUNT(*) as total
FROM ades_asistencias a
GROUP BY a.estudiante_id, a.estatus_asistencia;

\echo '✓ v_asistencias_resumen'

-- 3. Vista materializada: Conteo de tareas entregadas
CREATE MATERIALIZED VIEW IF NOT EXISTS v_tareas_entregas_resumen AS
SELECT 
  te.estudiante_id,
  COUNT(*) as total_entregas
FROM ades_tareas_entregas te
GROUP BY te.estudiante_id;

\echo '✓ v_tareas_entregas_resumen'

\echo ''
\echo '=== VERIFICACIÓN: MATERIALIZED VIEWS CREADAS ==='
SELECT matviewname FROM pg_matviews WHERE schemaname = 'public';

