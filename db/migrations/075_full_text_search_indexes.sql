-- =============================================================================
-- SPRINT 4 FASE 1: Full-Text Search Indexes (tsvector + GIN)
-- =============================================================================
-- Esta migración agrega índices GIN para búsqueda de texto completo en
-- columnas principales de personas, tareas, temas y materias.
-- Mejora: ~5-10x más rápido en búsquedas vs ILIKE
-- Tamaño: ~5 MB nuevos índices

\echo '=== CREANDO ÍNDICES FULL-TEXT SEARCH (TSVECTOR + GIN) ==='

-- 1. ades_personas (búsqueda por nombre completo)
\echo '✓ Índice FTS en ades_personas (nombre completo)'
CREATE INDEX IF NOT EXISTS idx_ades_personas_nombre_tsvector
ON ades_personas USING GIN (
  to_tsvector('spanish', COALESCE(nombre, '') || ' ' || 
              COALESCE(apellido_paterno, '') || ' ' || 
              COALESCE(apellido_materno, ''))
);

-- 2. ades_tareas (búsqueda en nombre + descripción)
\echo '✓ Índice FTS en ades_tareas (nombre + descripción)'
CREATE INDEX IF NOT EXISTS idx_ades_tareas_tsvector
ON ades_tareas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tarea, '') || ' ' || 
              COALESCE(descripcion, ''))
);

-- 3. ades_temas (búsqueda por nombre de tema)
\echo '✓ Índice FTS en ades_temas (nombre_tema)'
CREATE INDEX IF NOT EXISTS idx_ades_temas_nombre_tsvector
ON ades_temas USING GIN (
  to_tsvector('spanish', COALESCE(nombre_tema, ''))
);

-- 4. ades_materias (búsqueda por nombre de materia)
\echo '✓ Índice FTS en ades_materias (nombre_materia)'
CREATE INDEX IF NOT EXISTS idx_ades_materias_nombre_tsvector
ON ades_materias USING GIN (
  to_tsvector('spanish', COALESCE(nombre_materia, ''))
);

\echo ''
\echo '=== ÍNDICES FTS CREADOS EXITOSAMENTE ==='
SELECT COUNT(*) as fts_indexes_created FROM pg_indexes 
WHERE indexname LIKE '%tsvector%';

