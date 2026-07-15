/*
 * Archivo: 142_drop_suplencias_legacy_audit_columns.sql
 * Fecha: 2026-07-15
 *
 * Cierre del fix limpio de ades_suplencias (ver 140_hotfix_suplencias_entity_columns.sql y
 * la entidad mx.ades.modules.horarios.suplencias.Suplencia, ya remapeada a las columnas
 * canónicas heredando de mx.ades.common.AdesBaseEntity). El bff se reconstruyó y se verificó
 * estable (RestartCount=0) con la entidad nueva, que ya no mapea creado_el/creado_por/
 * actualizado_el/actualizado_por — se eliminan de forma segura.
 */
ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS creado_por;
ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS creado_el;
ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS actualizado_por;
ALTER TABLE public.ades_suplencias DROP COLUMN IF EXISTS actualizado_el;
