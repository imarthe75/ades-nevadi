-- =============================================================================
-- Migración 136: Corrección de Columnas de Auditoría en Tablas con Triggers
--   Asegura que todas las tablas que poseen el trigger audit_biu contengan
--   las columnas fecha_modificacion y usuario_modificacion para evitar fallos
--   de inserción y actualización.
-- =============================================================================
BEGIN;

-- 1. CORREGIR TABLA public.ades_cambios_grupo
ALTER TABLE public.ades_cambios_grupo 
  ADD COLUMN IF NOT EXISTS fecha_modificacion   TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS usuario_modificacion VARCHAR(150) DEFAULT CURRENT_USER;

-- 2. CORREGIR TABLA public.ades_rol_privilegios
ALTER TABLE public.ades_rol_privilegios 
  ADD COLUMN IF NOT EXISTS fecha_modificacion   TIMESTAMPTZ DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS usuario_modificacion VARCHAR(150) DEFAULT CURRENT_USER;

COMMIT;
