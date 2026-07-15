/*
 * Archivo: 140_hotfix_suplencias_entity_columns.sql
 * Fecha: 2026-07-15
 * Ambiente: desarrollo (host único = producción de infraestructura)
 *
 * INCIDENTE: la migración 138_fix_suplencias_and_cleanup.sql eliminó
 * (DROP COLUMN) las columnas creado_por/creado_el/actualizado_por/actualizado_el de
 * public.ades_suplencias al "estandarizar" la auditoría a las columnas canónicas
 * (fecha_creacion, etc.), pero la entidad JPA mx.ades.modules.horarios.suplencias.Suplencia
 * sigue mapeando esas 4 columnas (@Column(name="actualizado_el"), etc.). Con
 * hibernate.ddl-auto=validate, la SessionFactory falla al arrancar
 * ("Schema validation: missing column [actualizado_el]") y ades-bff entra en crash-loop:
 * el backend API queda completamente caído.
 *
 * HOTFIX (restauración de servicio): se re-agregan las 4 columnas que la entidad requiere,
 * backfilleadas desde las columnas canónicas. NO se tocan las columnas canónicas ni el
 * trigger audit_biu — la conformidad con la Regla #3 (columnas canónicas presentes) se
 * conserva. Queda deuda menor: dos convenciones de auditoría coexisten en esta tabla.
 *
 * FIX LIMPIO PENDIENTE (recomendado, requiere rebuild del bff): remapear las 4 @Column de
 * Suplencia.java a las columnas canónicas (creadoEl->fecha_creacion, creadoPor->usuario_creacion,
 * actualizadoEl->fecha_modificacion, actualizadoPor->usuario_modificacion) y luego eliminar
 * estas 4 columnas legacy. Ver docs/hallazgos/2026-07-15_validacion_remediacion.md.
 */

DO $$
BEGIN
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS creado_por TEXT;
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS creado_el TIMESTAMP;
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS actualizado_por TEXT;
    ALTER TABLE public.ades_suplencias ADD COLUMN IF NOT EXISTS actualizado_el TIMESTAMP;

    -- Backfill desde las columnas canónicas (todas presentes y pobladas)
    UPDATE public.ades_suplencias
    SET creado_el      = COALESCE(creado_el, fecha_creacion),
        creado_por     = COALESCE(creado_por, usuario_creacion),
        actualizado_el = COALESCE(actualizado_el, fecha_modificacion),
        actualizado_por= COALESCE(actualizado_por, usuario_modificacion);

    -- La entidad declara creado_el/creado_por como NOT NULL; el resto nullable
    ALTER TABLE public.ades_suplencias ALTER COLUMN creado_el SET NOT NULL;
    ALTER TABLE public.ades_suplencias ALTER COLUMN creado_por SET NOT NULL;

    RAISE NOTICE 'Hotfix ades_suplencias: columnas de la entidad restauradas';
END $$;
