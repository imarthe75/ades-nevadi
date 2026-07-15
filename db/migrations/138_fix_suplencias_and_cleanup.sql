/*
 * by Im@rthe
 * Fecha: 2026-07-15
 * Archivo: 138_fix_suplencias_and_cleanup.sql
 *
 * Descripcion:
 * 1. Incorpora las columnas canonicas de auditoria a public.ades_suplencias,
 *    mapea los datos existentes y elimina las columnas obsoletas.
 * 2. Elimina la tabla de respaldo temporal public.ades_pii_encryption_backup_20260619
 *    para cumplir con la minimizacion de datos de PII.
 *
 * Solicitado y aprobado por Israel Martínez Hernández
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- ==========================================
-- 1. Actualizar public.ades_suplencias
-- ==========================================
DO $$
BEGIN
    -- Agregar las nuevas columnas de auditoría
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS ref UUID DEFAULT gen_random_uuid();
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMPTZ;
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS fecha_modificacion TIMESTAMPTZ;
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS usuario_creacion TEXT;
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT;

    -- Copiar datos de las columnas viejas si existen
    UPDATE public.ades_suplencias
    SET fecha_creacion = COALESCE(creado_el, NOW()),
        usuario_creacion = COALESCE(creado_por, 'system'),
        fecha_modificacion = COALESCE(actualizado_el, creado_el, NOW()),
        usuario_modificacion = COALESCE(actualizado_por, creado_por, 'system')
    WHERE fecha_creacion IS NULL;

    -- Establecer NOT NULL en columnas requeridas
    ALTER TABLE public.ades_suplencias ALTER COLUMN ref SET NOT NULL;
    ALTER TABLE public.ades_suplencias ALTER COLUMN row_version SET NOT NULL;
    ALTER TABLE public.ades_suplencias ALTER COLUMN fecha_creacion SET NOT NULL;
    ALTER TABLE public.ades_suplencias ALTER COLUMN fecha_modificacion SET NOT NULL;
    ALTER TABLE public.ades_suplencias ALTER COLUMN usuario_creacion SET NOT NULL;
    ALTER TABLE public.ades_suplencias ALTER COLUMN usuario_modificacion SET NOT NULL;

    -- Eliminar el disparador viejo
    DROP TRIGGER IF EXISTS ades_suplencias_audit_biu ON public.ades_suplencias;

    -- Eliminar las columnas viejas obsoletas
    ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS creado_por;
    ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS creado_el;
    ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS actualizado_por;
    ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS actualizado_el;

    RAISE NOTICE 'Columnas de auditoría de public.ades_suplencias corregidas y datos migrados';
END $$;

-- Asignar el nuevo trigger de auditoría canonical
SELECT auditoria.asignar_biu('public.ades_suplencias');

-- ==========================================
-- 2. Eliminar tabla de respaldo temporal de PII
-- ==========================================
DROP TABLE IF EXISTS public.ades_pii_encryption_backup_20260619;
