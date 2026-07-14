-- =============================================================================
-- MIGRACION 128: ades_badge_otorgados — columnas usuario_creacion/modificacion
-- =============================================================================
-- Objetivo: regla 3 CLAUDE.md. El seed 007 (otorgamiento de badges) las
--           inserta explícitamente.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_badge_otorgados
    ADD COLUMN IF NOT EXISTS usuario_creacion     VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    ADD COLUMN IF NOT EXISTS usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER;

COMMIT;
