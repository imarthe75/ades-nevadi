-- =============================================================================
-- MIGRACION 129: ades_encuesta_respuestas — columnas de auditoría faltantes
-- =============================================================================
-- Objetivo: regla 3 CLAUDE.md. El seed 007 (respuestas de encuesta) inserta
--           is_active/usuario_creacion/usuario_modificacion explícitamente.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_encuesta_respuestas
    ADD COLUMN IF NOT EXISTS is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS usuario_creacion       VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    ADD COLUMN IF NOT EXISTS usuario_modificacion   VARCHAR(150) NOT NULL DEFAULT CURRENT_USER;

COMMIT;
