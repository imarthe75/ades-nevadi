-- ============================================================
-- Migración 046: Agregar materia_id a ades_foros
-- ============================================================
BEGIN;

ALTER TABLE public.ades_foros
    ADD COLUMN IF NOT EXISTS materia_id UUID REFERENCES public.ades_materias(id) ON DELETE SET NULL;

COMMENT ON COLUMN public.ades_foros.materia_id IS 'Materia asociada al foro interactivo (CO-021)';

COMMIT;
