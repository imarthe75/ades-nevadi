-- =============================================================================
-- SPRINT 3 FASE 4: VACUUM y ANALYZE - Optimizar Estadísticas
-- =============================================================================
-- Ejecutar VACUUM ANALYZE en tablas críticas para mejorar query planner

\echo '=== EJECUTANDO VACUUM ANALYZE EN TABLAS CRÍTICAS ==='

VACUUM ANALYZE ades_estudiantes;
\echo '✓ VACUUM ANALYZE ades_estudiantes'

VACUUM ANALYZE ades_personas;
\echo '✓ VACUUM ANALYZE ades_personas'

VACUUM ANALYZE ades_asistencias;
\echo '✓ VACUUM ANALYZE ades_asistencias'

VACUUM ANALYZE ades_calificaciones_periodo;
\echo '✓ VACUUM ANALYZE ades_calificaciones_periodo'

VACUUM ANALYZE ades_clases;
\echo '✓ VACUUM ANALYZE ades_clases'

VACUUM ANALYZE ades_usuarios;
\echo '✓ VACUUM ANALYZE ades_usuarios'

VACUUM ANALYZE ades_tareas_entregas;
\echo '✓ VACUUM ANALYZE ades_tareas_entregas'

VACUUM ANALYZE ades_inscripciones;
\echo '✓ VACUUM ANALYZE ades_inscripciones'

VACUUM ANALYZE ades_profesores;
\echo '✓ VACUUM ANALYZE ades_profesores'

VACUUM ANALYZE ades_grupos;
\echo '✓ VACUUM ANALYZE ades_grupos'

\echo ''
\echo '=== REINDEXAR TABLAS GRANDES ==='

REINDEX TABLE CONCURRENTLY ades_asistencias;
\echo '✓ REINDEX ades_asistencias'

REINDEX TABLE CONCURRENTLY ades_codigos_postales;
\echo '✓ REINDEX ades_codigos_postales'

REINDEX TABLE CONCURRENTLY ades_calificaciones_periodo;
\echo '✓ REINDEX ades_calificaciones_periodo'

\echo ''
\echo '=== VERIFICACIÓN: ESTADÍSTICAS ACTUALIZADAS ==='
SELECT 
  schemaname,
  COUNT(*) as table_count,
  pg_size_pretty(SUM(pg_total_relation_size(schemaname||'.'||tablename))) as total_size
FROM pg_tables 
WHERE schemaname = 'public'
GROUP BY schemaname;

