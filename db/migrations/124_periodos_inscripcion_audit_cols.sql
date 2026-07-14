-- =============================================================================
-- MIGRACION 124: ades_periodos_inscripcion — columnas de auditoría faltantes
-- =============================================================================
-- Objetivo: la tabla tenía fecha_creacion/usuario_creacion pero no ref,
--           fecha_modificacion ni usuario_modificacion (regla 3 CLAUDE.md),
--           y no tenía el trigger audit_biu asignado. El seed 007
--           (periodos de inscripción) fallaba al insertar.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_periodos_inscripcion
    ADD COLUMN IF NOT EXISTS ref                  UUID NOT NULL DEFAULT uuidv7(),
    ADD COLUMN IF NOT EXISTS fecha_modificacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS usuario_modificacion  VARCHAR(150) NOT NULL DEFAULT CURRENT_USER;

ALTER TABLE ades_periodos_inscripcion
    ALTER COLUMN fecha_creacion SET NOT NULL,
    ALTER COLUMN fecha_creacion SET DEFAULT NOW(),
    ALTER COLUMN usuario_creacion SET NOT NULL,
    ALTER COLUMN usuario_creacion SET DEFAULT CURRENT_USER,
    ALTER COLUMN is_active SET NOT NULL,
    ALTER COLUMN activo SET NOT NULL,
    ALTER COLUMN row_version SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ades_periodos_inscripcion_ref_key') THEN
        ALTER TABLE ades_periodos_inscripcion ADD CONSTRAINT ades_periodos_inscripcion_ref_key UNIQUE (ref);
    END IF;
END $$;

SELECT auditoria.asignar_biu('public.ades_periodos_inscripcion');

COMMIT;
