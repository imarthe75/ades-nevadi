/*
 * by Antigravity
 * Fecha: 2026-06-13
 * Archivo: 20260613_0001_validation_indexes.sql
 *
 * Descripcion:
 * Añade índices únicos condicionales en public.ades_personas y
 * public.ades_expediente_laboral para evitar RFCs y CURPs duplicados
 * en registros activos.
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

-- =========================================================================
-- 1. Índice único para RFC en public.ades_personas (sólo para activos y no nulos)
-- =========================================================================
CREATE UNIQUE INDEX IF NOT EXISTS idx_personas_unique_rfc
    ON public.ades_personas (rfc)
    WHERE rfc IS NOT NULL AND is_active = TRUE;

-- =========================================================================
-- 2. Índices únicos para CURP y RFC en public.ades_expediente_laboral
-- =========================================================================
CREATE UNIQUE INDEX IF NOT EXISTS idx_exp_laboral_unique_curp
    ON public.ades_expediente_laboral (curp)
    WHERE curp IS NOT NULL AND is_active = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_exp_laboral_unique_rfc
    ON public.ades_expediente_laboral (rfc)
    WHERE rfc IS NOT NULL AND is_active = TRUE;

-- =========================================================================
-- 3. Mensaje de confirmación
-- =========================================================================
DO $$ BEGIN
    RAISE NOTICE '=== Migración aplicada: Índices únicos para CURP y RFC ===';
    RAISE NOTICE 'idx_personas_unique_rfc';
    RAISE NOTICE 'idx_exp_laboral_unique_curp';
    RAISE NOTICE 'idx_exp_laboral_unique_rfc';
END $$;
