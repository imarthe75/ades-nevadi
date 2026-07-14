-- =============================================================================
-- MIGRACION 127: ades_notificaciones_sistema — columnas de auditoría faltantes
-- =============================================================================
-- Objetivo: regla 3 CLAUDE.md — la tabla solo tenía fecha_creacion. El seed 007
--           (notificaciones de sistema) inserta is_active/usuario_creacion/
--           usuario_modificacion explícitamente.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_notificaciones_sistema
    ADD COLUMN IF NOT EXISTS is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS fecha_modificacion     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS usuario_creacion       VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    ADD COLUMN IF NOT EXISTS usuario_modificacion   VARCHAR(150) NOT NULL DEFAULT CURRENT_USER;

COMMIT;
