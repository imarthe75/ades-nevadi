-- =============================================================================
-- Migración: 107_planeacion_pendiente_reprogramar.sql
-- Descripción: OA-012 — cuando una clase se marca SUSPENDIDA, los temas
--              planeados para esa fecha+grupo quedan marcados para
--              reprogramar en vez de perderse silenciosamente.
-- Tablas afectadas: ades_planeacion_clases (ALTER)
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

ALTER TABLE ades_planeacion_clases
    ADD COLUMN IF NOT EXISTS pendiente_reprogramar BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ades_planeacion_clases.pendiente_reprogramar IS
    'TRUE cuando la clase de esa fecha+grupo fue marcada SUSPENDIDA — el docente debe reprogramar el tema a otra fecha.';
