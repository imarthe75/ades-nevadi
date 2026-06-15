-- 053_personas_pais_nacimiento.sql
-- Agrega pais_nacimiento a ades_personas (default México)

ALTER TABLE public.ades_personas
    ADD COLUMN IF NOT EXISTS pais_nacimiento VARCHAR(100) DEFAULT 'México';

COMMENT ON COLUMN public.ades_personas.pais_nacimiento
    IS 'País de nacimiento (texto). Si es México, estado/municipio usan catálogo SEPOMEX.';

-- Registros existentes: si no tienen pais_nacimiento, asumir México
UPDATE public.ades_personas
SET pais_nacimiento = 'México'
WHERE pais_nacimiento IS NULL;
