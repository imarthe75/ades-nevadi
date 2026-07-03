-- =============================================================================
-- Migración 102 — Fix: ades_expediente_documentos sin columna is_active
--   Bug preexistente descubierto durante la verificación de PE-023 (expediente
--   lite): ExpedienteQueryService.java (obtenerExpediente, previewDocumento,
--   documentoById, buscar por OCR, ver documento) filtra por
--   "WHERE ... AND is_active = TRUE" contra ades_expediente_documentos, pero
--   la migración 037 nunca agregó esa columna a esta tabla en particular
--   (sí la tiene la tabla padre ades_expedientes_alumno, y las tablas
--   hermanas ades_bajas/ades_extraordinarias/ades_constancias). Resultado:
--   GET /expediente/alumno/{id} lanzaba 500 (columna inexistente) en cada
--   llamada — nunca funcionó en producción.
-- =============================================================================

ALTER TABLE public.ades_expediente_documentos
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN public.ades_expediente_documentos.is_active
    IS 'Soft-delete — consistente con el resto de tablas ades_expediente_*. Faltaba desde mig 037.';
