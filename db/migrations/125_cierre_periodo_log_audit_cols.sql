-- =============================================================================
-- MIGRACION 125: ades_cierre_periodo_log — columnas usuario_creacion/modificacion
-- =============================================================================
-- Objetivo: la tabla ya tiene el trigger audit_biu (trg_aud_biu) asignado, que
--           asigna usuario_creacion/usuario_modificacion incondicionalmente,
--           pero las columnas no existían. El seed 007 (cierre de periodo)
--           fallaba al insertar.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_cierre_periodo_log
    ADD COLUMN IF NOT EXISTS usuario_creacion     VARCHAR(150) NOT NULL DEFAULT CURRENT_USER,
    ADD COLUMN IF NOT EXISTS usuario_modificacion VARCHAR(150) NOT NULL DEFAULT CURRENT_USER;

COMMIT;
