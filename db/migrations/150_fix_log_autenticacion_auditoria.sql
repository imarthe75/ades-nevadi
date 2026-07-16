/*
 * by Im@rthe
 * Fecha: 2026-07-16
 * Archivo: 150_fix_log_autenticacion_auditoria.sql
 *
 * Descripcion:
 * Cierra el único hueco real persistente del censo completo de auditoría de las
 * 184 tablas ades_* (docs/hallazgos/2026-07-15_reporte..., confirmado de nuevo en
 * docs/hallazgos/2026-07-16_auditoria_gaps_no_revisados.md #5): ades_log_autenticacion
 * tenía solo la columna `ref` (sin default consistente) y ninguna de las otras 5
 * columnas de auditoría exigidas por la Regla Mandatoria #3, ni el trigger audit_biu
 * de la Regla #4. Se usa `fecha_login` (ya existente, poblada en todas las filas)
 * como fuente de backfill de fecha_creacion/fecha_modificacion — coherente porque
 * un registro de log de autenticación nunca se actualiza tras crearse.
 *
 * Solicitado y aprobado por Israel Martínez Hernández
 *
 * Motor de Base de Datos: PostgreSQL
 * Version minima requerida: 18
 * Ambiente: desarrollo
 */

DO $$
BEGIN
    -- ref ya existía pero sin garantía de NOT NULL/default consistente en filas viejas
    ALTER TABLE public.ades_log_autenticacion ALTER COLUMN ref SET DEFAULT uuidv7();
    UPDATE public.ades_log_autenticacion SET ref = uuidv7() WHERE ref IS NULL;
    ALTER TABLE public.ades_log_autenticacion ALTER COLUMN ref SET NOT NULL;

    ALTER TABLE public.ades_log_autenticacion ADD COLUMN IF NOT EXISTS row_version INTEGER DEFAULT 1;
    ALTER TABLE public.ades_log_autenticacion ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMPTZ;
    ALTER TABLE public.ades_log_autenticacion ADD COLUMN IF NOT EXISTS fecha_modificacion TIMESTAMPTZ;
    ALTER TABLE public.ades_log_autenticacion ADD COLUMN IF NOT EXISTS usuario_creacion TEXT;
    ALTER TABLE public.ades_log_autenticacion ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT;

    UPDATE public.ades_log_autenticacion
    SET fecha_creacion = COALESCE(fecha_creacion, fecha_login, NOW()),
        fecha_modificacion = COALESCE(fecha_modificacion, fecha_login, NOW()),
        usuario_creacion = COALESCE(usuario_creacion, 'system'),
        usuario_modificacion = COALESCE(usuario_modificacion, 'system')
    WHERE fecha_creacion IS NULL;

    ALTER TABLE public.ades_log_autenticacion ALTER COLUMN row_version SET NOT NULL;
    ALTER TABLE public.ades_log_autenticacion ALTER COLUMN fecha_creacion SET NOT NULL;
    ALTER TABLE public.ades_log_autenticacion ALTER COLUMN fecha_modificacion SET NOT NULL;
    ALTER TABLE public.ades_log_autenticacion ALTER COLUMN usuario_creacion SET NOT NULL;
    ALTER TABLE public.ades_log_autenticacion ALTER COLUMN usuario_modificacion SET NOT NULL;

    RAISE NOTICE 'Columnas de auditoría de public.ades_log_autenticacion completadas';
END $$;

SELECT auditoria.asignar_biu('public.ades_log_autenticacion');
