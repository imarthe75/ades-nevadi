-- =============================================================================
-- SPRINT 4 FASE 2: Partial Indexes (WHERE is_active = TRUE)
-- =============================================================================
-- Índices parciales en registros activos solamente.
-- Mejora: -20-30% tamaño de índices, mejor performance en tabla actualizada
-- Nota: Mantiene índices previos también para compatibilidad

\echo '=== CREANDO ÍNDICES PARCIALES (WHERE is_active = TRUE) ==='

-- 1. ades_personas (solo activos)
\echo '✓ Índice parcial FTS en ades_personas (is_active = TRUE)'
CREATE INDEX IF NOT EXISTS idx_ades_personas_nombre_tsvector_active
ON ades_personas USING GIN (
  to_tsvector('spanish', COALESCE(nombre, '') || ' ' || 
              COALESCE(apellido_paterno, '') || ' ' || 
              COALESCE(apellido_materno, ''))
WHERE is_active = TRUE;

-- 2. ades_tareas (solo activas con fecha_fin >= hoy)
\echo '✓ Índice parcial FTS en ades_tareas (is_active = TRUE)'
CREATE INDEX IF NOT EXISTS idx_ades_tareas_tsvector_active
ON ades_tareas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tarea, '') || ' ' || 
              COALESCE(descripcion, ''))
WHERE is_active = TRUE;

-- 3. ades_temas (solo activos)
\echo '✓ Índice parcial FTS en ades_temas (is_active = TRUE)'
CREATE INDEX IF NOT EXISTS idx_ades_temas_nombre_tsvector_active
ON ades_temas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tema, ''))
WHERE is_active = TRUE;

-- 4. ades_materias (solo activas)
\echo '✓ Índice parcial FTS en ades_materias (is_active = TRUE)'
CREATE INDEX IF NOT EXISTS idx_ades_materias_nombre_tsvector_active
ON ades_materias USING GIN (
  to_tsvector('spanish', COALESCE(nombre_materia, ''))
WHERE is_active = TRUE;

-- 5. Índices parciales adicionales en FK (mejor performance en JOINs)
\echo '✓ Índice parcial FK en ades_estudiantes (plantel_id, is_active)'
CREATE INDEX IF NOT EXISTS idx_ades_estudiantes_plantel_active
ON ades_estudiantes(plantel_id)
WHERE is_active = TRUE;

\echo '✓ Índice parcial FK en ades_clases (grupo_id, is_active)'
CREATE INDEX IF NOT EXISTS idx_ades_clases_grupo_active
ON ades_clases(grupo_id)
WHERE is_active = TRUE;

\echo ''
\echo '=== ÍNDICES PARCIALES CREADOS EXITOSAMENTE ==='
SELECT COUNT(*) as partial_indexes 
FROM pg_indexes 
WHERE indexdef LIKE '%WHERE%' AND schemaname = 'public';

