-- =============================================================================
-- Migración 146: Corrección de claves municipales de SEPOMEX
-- Corrige Metepec (de 106 a 054), Tenancingo (de 099 a 088), Ixtapan de la Sal (de 057 a 040)
-- Elimina códigos postales y asentamientos huérfanos importados con claves erróneas
-- =============================================================================
BEGIN;

-- 1. Actualizar claves de municipios a sus valores oficiales de SEPOMEX/INEGI
UPDATE public.ades_municipios
SET clave_municipio = '054', fecha_modificacion = NOW(), usuario_modificacion = 'migration'
WHERE nombre_municipio = 'Metepec';

UPDATE public.ades_municipios
SET clave_municipio = '088', fecha_modificacion = NOW(), usuario_modificacion = 'migration'
WHERE nombre_municipio = 'Tenancingo';

UPDATE public.ades_municipios
SET clave_municipio = '040', fecha_modificacion = NOW(), usuario_modificacion = 'migration'
WHERE nombre_municipio = 'Ixtapan de la Sal';

-- 2. Limpieza de datos antiguos erróneos para permitir re-sincronización limpia
-- Solo mantener los registros asociados a las direcciones de planteles existentes.
DELETE FROM public.ades_codigos_postales
WHERE id NOT IN (
    SELECT DISTINCT codigo_postal_id 
    FROM public.ades_direcciones 
    WHERE codigo_postal_id IS NOT NULL
);

DELETE FROM public.ades_localidades
WHERE id NOT IN (
    SELECT DISTINCT localidad_id 
    FROM public.ades_direcciones 
    WHERE localidad_id IS NOT NULL
)
AND id NOT IN (
    SELECT DISTINCT localidad_id 
    FROM public.ades_codigos_postales 
    WHERE localidad_id IS NOT NULL
);

COMMIT;
