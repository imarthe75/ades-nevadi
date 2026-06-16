-- =============================================================================
-- SPRINT 3 FASE 2B: Corregir Índices Compuestos (Schema Real)
-- =============================================================================
-- Crear índices compuestos correctos basados en schema real

\echo '=== CREANDO ÍNDICES COMPUESTOS CORRECTOS ==='

-- Índice compuesto para queries frecuentes de asistencia
-- Usar fecha_creacion en lugar de fecha que no existe
CREATE INDEX IF NOT EXISTS idx_ades_asistencias_estudiante_clase_estado 
  ON ades_asistencias(estudiante_id, clase_id, estatus_asistencia);
\echo '✓ idx_ades_asistencias_estudiante_clase_estado'

-- Índice compuesto para queries de calificaciones
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_periodo_estudiante_calificacion
  ON ades_calificaciones_periodo(estudiante_id, calificacion_final, es_acreditado);
\echo '✓ idx_ades_calificaciones_periodo_estudiante_calificacion'

-- Índice compuesto para búsquedas de personas
CREATE INDEX IF NOT EXISTS idx_ades_personas_apellido_nombre
  ON ades_personas(apellido, nombre);
\echo '✓ idx_ades_personas_apellido_nombre'

-- Índice compuesto para inscripciones activas
CREATE INDEX IF NOT EXISTS idx_ades_inscripciones_estudiante_activo
  ON ades_inscripciones(estudiante_id, is_active);
\echo '✓ idx_ades_inscripciones_estudiante_activo'

-- Índice compuesto para búsquedas en tareas
CREATE INDEX IF NOT EXISTS idx_ades_tareas_clase_fecha_creacion
  ON ades_tareas(clase_id, fecha_creacion);
\echo '✓ idx_ades_tareas_clase_fecha_creacion'

\echo ''
\echo '=== VERIFICACIÓN: ÍNDICES COMPUESTOS CREADOS ==='
SELECT COUNT(*) as total_indexes FROM pg_indexes WHERE schemaname = 'public';

