-- =============================================================================
-- Migración: 114_fix_auditoria_historial_admision.sql
-- Descripción: Fix de cumplimiento — la migración 110 creó
--              ades_admision_historial_estados sin las columnas de auditoría
--              obligatorias (CLAUDE.md regla 3/4) ni la llamada a
--              auditoria.asignar_biu(). Hallazgo de auditoría de seguridad y
--              documentación 2026-07-03. Esta tabla es un log de solo-INSERT
--              (cada fila es una transición inmutable), por lo que
--              row_version/fecha_modificacion/usuario_modificacion quedarán
--              siempre en su valor inicial — se agregan igualmente por
--              consistencia con el estándar y con auditoria.reporte_cobertura().
-- Tablas afectadas: ades_admision_historial_estados (ALTER)
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

ALTER TABLE ades_admision_historial_estados
    ADD COLUMN IF NOT EXISTS ref                  UUID,
    ADD COLUMN IF NOT EXISTS row_version          INTEGER,
    ADD COLUMN IF NOT EXISTS fecha_creacion        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS fecha_modificacion    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS usuario_creacion      TEXT,
    ADD COLUMN IF NOT EXISTS usuario_modificacion  TEXT;

-- Backfill de fecha_creacion para las filas ya insertadas por la mig 110/backfill.
UPDATE ades_admision_historial_estados
SET fecha_creacion = fecha
WHERE fecha_creacion IS NULL;

SELECT auditoria.asignar_biu('public.ades_admision_historial_estados');
