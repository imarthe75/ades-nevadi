-- =============================================================================
-- Migración 147: Normalización física 3NF de SEPOMEX
-- 1. Agregar columna codigo_postal a la tabla ades_localidades
-- 2. Migrar la información y eliminar la tabla fisica redundante ades_codigos_postales
-- 3. Crear una vista compatible para no romper consultas existentes del backend (BFF)
-- =============================================================================
BEGIN;

-- 1. Agregar columna a la tabla de localidades
ALTER TABLE public.ades_localidades 
  ADD COLUMN IF NOT EXISTS codigo_postal VARCHAR(10);

-- 2. Migrar la información existente de códigos postales
UPDATE public.ades_localidades l
SET codigo_postal = cp.codigo_postal
FROM public.ades_codigos_postales cp
WHERE cp.localidad_id = l.id;

-- 3. Actualizar la tabla de direcciones para asegurar que localidad_id esté lleno
UPDATE public.ades_direcciones d
SET localidad_id = cp.localidad_id
FROM public.ades_codigos_postales cp
WHERE d.codigo_postal_id = cp.id AND d.localidad_id IS NULL;

-- 4. Eliminar restricciones de llave foránea en direcciones hacia la tabla física
ALTER TABLE public.ades_direcciones DROP CONSTRAINT IF EXISTS ades_direcciones_codigo_postal_id_fkey;
ALTER TABLE public.ades_direcciones DROP CONSTRAINT IF EXISTS fk_ades_dir_cp;

-- 5. Eliminar la tabla física redundante
DROP TABLE IF EXISTS public.ades_codigos_postales CASCADE;

-- 6. Crear la vista de compatibilidad ades_codigos_postales
-- Esta vista expone exactamente el mismo esquema de la tabla física original
CREATE OR REPLACE VIEW public.ades_codigos_postales AS
SELECT
    l.id AS id,
    l.codigo_postal AS codigo_postal,
    l.id AS localidad_id,
    l.municipio_id AS municipio_id,
    m.estado_id AS estado_id,
    l.tipo_asentamiento_id AS tipo_asentamiento_id,
    l.ref AS ref,
    l.is_active AS is_active,
    l.fecha_creacion AS fecha_creacion,
    l.fecha_modificacion AS fecha_modificacion,
    l.usuario_creacion AS usuario_creacion,
    l.usuario_modificacion AS usuario_modificacion,
    l.row_version AS row_version
FROM public.ades_localidades l
JOIN public.ades_municipios m ON m.id = l.municipio_id;

-- 7. Hacer la columna codigo_postal obligatoria en localidades para asegurar integridad
ALTER TABLE public.ades_localidades ALTER COLUMN codigo_postal SET NOT NULL;

COMMIT;
