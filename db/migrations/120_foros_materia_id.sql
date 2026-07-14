-- =============================================================================
-- MIGRACION 120: ades_foros — columna materia_id faltante
-- =============================================================================
-- Objetivo: la entidad JPA mx.ades.modules.foros.Foro define materiaId
--           (@Column materia_id, FK a ades_materias) pero nunca se creó la
--           columna correspondiente — Hibernate schema validation fallaba
--           en boot.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_foros
    ADD COLUMN IF NOT EXISTS materia_id UUID REFERENCES ades_materias(id);

COMMENT ON COLUMN ades_foros.materia_id IS 'Materia asociada al foro de discusión (opcional)';

COMMIT;
