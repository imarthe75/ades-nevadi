-- =============================================================================
-- MIGRACION 121: ades_reinscripcion_ciclo — columna ref faltante (regla 3 CLAUDE.md)
-- =============================================================================
-- Objetivo: la entidad JPA mx.ades.modules.reinscripcion.ReinscripcionCiclo
--           extiende AdesBaseEntity (requiere columna ref uuidv7 inmutable)
--           pero la tabla nunca la tuvo — Hibernate schema validation fallaba
--           en boot. El trigger trg_aud_biu ya existía pero sin la columna
--           que gestiona.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

ALTER TABLE ades_reinscripcion_ciclo
    ADD COLUMN IF NOT EXISTS ref UUID NOT NULL DEFAULT uuidv7();

ALTER TABLE ades_reinscripcion_ciclo
    ADD CONSTRAINT ades_reinscripcion_ciclo_ref_key UNIQUE (ref);

COMMENT ON COLUMN ades_reinscripcion_ciclo.ref IS 'Referencia externa inmutable (uuidv7) — regla 3 CLAUDE.md';

COMMIT;
