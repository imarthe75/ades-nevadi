-- =============================================================================
-- Migración: 105_planes_estudio_alt_nee.sql
-- Descripción: AC-014 — planes de estudio alternativos/reducidos para alumnos con
--              Necesidades Educativas Especiales (NEE). Permite asociar a un
--              alumno (o a un grupo NEE completo) un subconjunto de materias
--              distinto al plan estándar de su grado, sin modificar el plan base.
-- Tablas afectadas: ades_planes_estudio_alt (nueva), ades_planes_estudio_alt_materias (nueva)
-- Dependencias: ades_estudiantes, ades_grupos, ades_materias, auditoria.asignar_biu()
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

CREATE TABLE IF NOT EXISTS ades_planes_estudio_alt (
    id                   UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    estudiante_id        UUID REFERENCES ades_estudiantes(id),
    grupo_id             UUID REFERENCES ades_grupos(id),
    motivo               TEXT NOT NULL,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    ref                  UUID,
    row_version          INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT,
    CONSTRAINT ck_plan_alt_alumno_o_grupo CHECK (
        (estudiante_id IS NOT NULL AND grupo_id IS NULL) OR
        (estudiante_id IS NULL AND grupo_id IS NOT NULL)
    )
);

COMMENT ON TABLE ades_planes_estudio_alt IS
    'Plan de estudio alternativo/reducido (NEE) — aplica a un alumno específico o a un grupo NEE completo, nunca ambos.';

CREATE TABLE IF NOT EXISTS ades_planes_estudio_alt_materias (
    id                   UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    plan_alt_id          UUID NOT NULL REFERENCES ades_planes_estudio_alt(id) ON DELETE CASCADE,
    materia_id           UUID NOT NULL REFERENCES ades_materias(id),
    horas_semana         NUMERIC(4,1),
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    ref                  UUID,
    row_version          INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT,
    CONSTRAINT uq_plan_alt_materia UNIQUE (plan_alt_id, materia_id)
);

SELECT auditoria.asignar_biu('public.ades_planes_estudio_alt');
SELECT auditoria.asignar_biu('public.ades_planes_estudio_alt_materias');
