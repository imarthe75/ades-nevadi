-- Migration 115: Add Foreign Key Indexes (SEMANA 2 — ADES Audit)
-- Timeline: 2026-07-15
-- Impact: -50% query latency on large table scans
-- Safety: CONCURRENTLY flag prevents table locks

-- ============================================================================
-- CRITICAL FK INDEXES (5 tables > 50MB each)
-- ============================================================================

-- 1. ades_calificaciones_periodo_ciclo_2025_2026 (59 MB)
--    Used in: close periods, audit reports, dashboards
CREATE INDEX CONCURRENTLY idx_calificaciones_periodo_2025_2026_cerrado_por
  ON ades_calificaciones_periodo_ciclo_2025_2026(cerrado_por)
  WHERE cerrado_por IS NOT NULL;

-- 2. ades_tareas_entregas (86 MB)
--    Used in: grade corrections, audit, completion tracking
CREATE INDEX CONCURRENTLY idx_tareas_entregas_calificado_por
  ON ades_tareas_entregas(calificado_por)
  WHERE calificado_por IS NOT NULL;

-- 3. ades_codigos_postales (135 MB)
--    Used in: address lookups, census reports, enrollment
CREATE INDEX CONCURRENTLY idx_codigos_postales_estado_id
  ON ades_codigos_postales(estado_id)
  WHERE estado_id IS NOT NULL;

-- 4. ades_calificaciones_historico (58 MB)
--    Used in: historical queries, audit trail, analytics
CREATE INDEX CONCURRENTLY idx_calificaciones_historico_usuario_id
  ON ades_calificaciones_historico(usuario_id)
  WHERE usuario_id IS NOT NULL;

-- ============================================================================
-- HIGH-IMPACT FK INDEXES (commonly used in queries/joins)
-- ============================================================================

-- 5. ades_estudiantes (lookup by persona)
CREATE INDEX CONCURRENTLY idx_estudiantes_persona_id
  ON ades_estudiantes(persona_id)
  WHERE persona_id IS NOT NULL;

-- 6. ades_grupos (lookup by profesor/aula)
CREATE INDEX CONCURRENTLY idx_grupos_profesor_titular_id
  ON ades_grupos(profesor_titular_id)
  WHERE profesor_titular_id IS NOT NULL;

CREATE INDEX CONCURRENTLY idx_grupos_aula_id
  ON ades_grupos(aula_id)
  WHERE aula_id IS NOT NULL;

-- 7. ades_tareas (multi-column lookups)
CREATE INDEX CONCURRENTLY idx_tareas_tema_id
  ON ades_tareas(tema_id)
  WHERE tema_id IS NOT NULL;

CREATE INDEX CONCURRENTLY idx_tareas_plan_trabajo_id
  ON ades_tareas(plan_trabajo_id)
  WHERE plan_trabajo_id IS NOT NULL;

-- 8. ades_usuarios (audit trail, session tracking)
CREATE INDEX CONCURRENTLY idx_usuarios_persona_id
  ON ades_usuarios(persona_id)
  WHERE persona_id IS NOT NULL;

-- 9. ades_expediente_documentos (document lookups)
CREATE INDEX CONCURRENTLY idx_expediente_documentos_cargado_por
  ON ades_expediente_documentos(cargado_por)
  WHERE cargado_por IS NOT NULL;

-- 10. ades_profesores (staff lookups)
CREATE INDEX CONCURRENTLY idx_profesores_persona_id
  ON ades_profesores(persona_id)
  WHERE persona_id IS NOT NULL;

-- ============================================================================
-- VERIFICATION
-- ============================================================================

-- Verification queries (run manually post-migration):
-- SELECT COUNT(*) FROM pg_indexes WHERE indexname LIKE 'idx_%_id%';
-- EXPLAIN ANALYZE SELECT COUNT(*) FROM ades_calificaciones_periodo_ciclo_2025_2026 WHERE cerrado_por IS NOT NULL;
-- Expected: Index Scan on idx_calificaciones_periodo_2025_2026_cerrado_por (execution time < 5ms)
