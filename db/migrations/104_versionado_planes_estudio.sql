-- =============================================================================
-- Migración: 104_versionado_planes_estudio.sql
-- Descripción: AC-015 — permite publicar/archivar versiones de un plan de estudio
--              (materia asignada a grado+ciclo) sin eliminarlo. Antes solo existía
--              soft-delete (is_active) sin distinguir borrador de publicado.
-- Tablas afectadas: ades_materias_plan (ALTER)
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

ALTER TABLE ades_materias_plan
    ADD COLUMN IF NOT EXISTS estado_publicacion VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO'
        CHECK (estado_publicacion IN ('BORRADOR', 'PUBLICADO', 'ARCHIVADO')),
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN ades_materias_plan.estado_publicacion IS
    'BORRADOR: en edición, no visible en vistas operativas. PUBLICADO: activo para el ciclo. ARCHIVADO: histórico, ya no editable.';
COMMENT ON COLUMN ades_materias_plan.version IS
    'Incrementa cada vez que se archiva una versión y se publica una nueva para el mismo grado+ciclo.';
