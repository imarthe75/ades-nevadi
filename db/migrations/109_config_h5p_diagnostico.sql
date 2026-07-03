-- =============================================================================
-- Migración: 109_config_h5p_diagnostico.sql
-- Descripción: PE-003 — vincula un contenido H5P (cuestionario) por nivel
--              educativo al flujo de admisión, para automatizar la evaluación
--              diagnóstica en vez de captura manual. Reusa la tabla genérica
--              ades_config (ya usada por NEM Fase 3) — sin tabla nueva.
--              El content_id real de H5P se completa desde
--              GET/PATCH /admin/config (grupo=admision) una vez que el
--              administrador cree el cuestionario en el módulo H5P.
-- Tablas afectadas: ades_config (INSERT)
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

INSERT INTO ades_config (clave, valor, descripcion, grupo, tipo_valor, es_editable)
VALUES (
    'h5p_diagnostico_contenido_ids',
    '{"PRIMARIA": null, "SECUNDARIA": null, "PREPARATORIA": null}'::jsonb,
    'ID de contenido H5P (cuestionario) por nivel para la evaluación diagnóstica automatizada de admisión (PE-003). Completar desde el módulo H5P una vez creado el cuestionario.',
    'admision',
    'json',
    TRUE
)
ON CONFLICT DO NOTHING;
