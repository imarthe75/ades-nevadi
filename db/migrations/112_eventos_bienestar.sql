-- =============================================================================
-- Migración: 112_eventos_bienestar.sql
-- Descripción: SB-023 — eventos de bienestar institucional (día de la
--              amabilidad, actividades lúdicas, etc.) con conteo simple de
--              participantes. CRUD simple, mismo patrón que otros catálogos
--              operativos del proyecto (comunicados/capacitaciones).
-- Tablas afectadas: ades_eventos_bienestar (nueva)
-- Dependencias: ades_planteles, auditoria.asignar_biu()
-- Autor: ADES
-- Fecha: 2026-07-03
-- =============================================================================

CREATE TABLE IF NOT EXISTS ades_eventos_bienestar (
    id                   UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    titulo               VARCHAR(200) NOT NULL,
    descripcion          TEXT,
    fecha                DATE NOT NULL,
    plantel_id           UUID REFERENCES ades_planteles(id),
    tipo                 VARCHAR(30) NOT NULL DEFAULT 'ACTIVIDAD_LUDICA'
                           CHECK (tipo IN ('ACTIVIDAD_LUDICA', 'DIA_TEMATICO', 'TALLER_BIENESTAR', 'OTRO')),
    participantes_count  INTEGER NOT NULL DEFAULT 0,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    ref                  UUID,
    row_version          INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

COMMENT ON TABLE ades_eventos_bienestar IS
    'SB-023 — eventos de bienestar institucional (día de la amabilidad, actividades lúdicas, talleres).';

SELECT auditoria.asignar_biu('public.ades_eventos_bienestar');
