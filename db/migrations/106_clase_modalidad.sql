-- =============================================================================
-- Migración: 106_clase_modalidad.sql
-- Descripción: OA-006 — distingue clase presencial vs remota/híbrida. Mismo
--              patrón que ModalidadCapacitacion (capacitaciones), aplicado a
--              ades_clases.
-- Tablas afectadas: ades_clases (ALTER)
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

ALTER TABLE ades_clases
    ADD COLUMN IF NOT EXISTS modalidad VARCHAR(20) NOT NULL DEFAULT 'PRESENCIAL'
        CHECK (modalidad IN ('PRESENCIAL', 'REMOTA', 'HIBRIDA'));

COMMENT ON COLUMN ades_clases.modalidad IS 'PRESENCIAL / REMOTA / HIBRIDA — permite reportar el tipo de sesión post-pandemia.';
