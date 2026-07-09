-- Migration 115-V2: Add remaining FK indexes that failed
-- This completes the FK index migration (ades_calificaciones_historico fix)

-- Fix: ades_calificaciones_historico uses cierre_id (not usuario_id)
CREATE INDEX CONCURRENTLY idx_calificaciones_historico_cierre_id
  ON ades_calificaciones_historico(cierre_id)
  WHERE cierre_id IS NOT NULL;

-- Verification
SELECT COUNT(*) as "Total New Indexes" FROM pg_indexes 
WHERE indexname LIKE 'idx_%_id%' AND schemaname = 'public';
