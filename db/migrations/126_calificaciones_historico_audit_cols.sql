-- =============================================================================
-- MIGRACION 126: ades_calificaciones_historico — columnas usuario_creacion/modificacion
-- =============================================================================
-- Objetivo: tabla de snapshot histórico (inmutable) sin columnas de usuario
--           (regla 3 CLAUDE.md). El seed 007 (cierre de periodo → histórico)
--           las inserta explícitamente al tomar el snapshot.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_calificaciones_historico
    ADD COLUMN IF NOT EXISTS usuario_creacion     VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    ADD COLUMN IF NOT EXISTS usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER;

COMMIT;
