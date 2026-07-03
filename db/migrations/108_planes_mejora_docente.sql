-- =============================================================================
-- Migración: 108_planes_mejora_docente.sql
-- Descripción: DP-016 — plan de mejora automático generado a partir de los
--              criterios con calificación baja de una evaluación docente 360°.
--              Recomendaciones basadas en reglas (mapeo criterio→sugerencia),
--              no IA — consistente con mantenibilidad por equipo pequeño.
-- Tablas afectadas: ades_planes_mejora_docente (nueva)
-- Dependencias: ades_evaluacion_docente, auditoria.asignar_biu()
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

CREATE TABLE IF NOT EXISTS ades_planes_mejora_docente (
    id                   UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    evaluacion_id        UUID NOT NULL REFERENCES ades_evaluacion_docente(id),
    criterio_debil       TEXT NOT NULL,
    calificacion         SMALLINT NOT NULL,
    recomendacion        TEXT NOT NULL,
    fecha_seguimiento    DATE,
    estado               VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                           CHECK (estado IN ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADO')),
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    ref                  UUID,
    row_version          INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

COMMENT ON TABLE ades_planes_mejora_docente IS
    'Plan de mejora generado por regla (no IA) a partir de criterios con calificación baja en una evaluación docente 360°.';

SELECT auditoria.asignar_biu('public.ades_planes_mejora_docente');
