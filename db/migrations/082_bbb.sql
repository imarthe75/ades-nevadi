-- =============================================================================
-- Migración: 082_bbb.sql
-- Descripción: Crea las tablas para videoconferencias BigBlueButton (BBB):
--              reuniones programadas (clase, tutoría, reunión de padres, etc.),
--              grabaciones de sesiones y registro de asistencia a videoconferencias.
-- Tablas afectadas: ades_bbb_reuniones, ades_bbb_grabaciones, ades_bbb_asistencia
-- Dependencias: ades_grupos, ades_planteles, ades_personas
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- =============================================================================
-- MIGRACIÓN 082 — FASE 26: BigBlueButton — Videoconferencias Institucionales
-- =============================================================================

-- Reuniones/sesiones BBB programadas
CREATE TABLE IF NOT EXISTS ades_bbb_reuniones (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    meeting_id            TEXT        NOT NULL UNIQUE,  -- BBB internal meeting ID (UUID generado por ADES)
    nombre                TEXT        NOT NULL,
    descripcion           TEXT,
    tipo                  TEXT        NOT NULL DEFAULT 'CLASE'
                            CHECK (tipo IN ('CLASE','TUTORIA','REUNION_PADRES','ASESORIA','CAPACITACION','EVENTO')),
    grupo_id              UUID        REFERENCES ades_grupos(id),
    plantel_id            UUID        REFERENCES ades_planteles(id),
    organiza_persona_id   UUID        REFERENCES ades_personas(id),
    fecha_programada      TIMESTAMPTZ NOT NULL,
    duracion_max_min      INTEGER     NOT NULL DEFAULT 60,
    password_moderador    TEXT        NOT NULL,
    password_asistente    TEXT        NOT NULL,
    grabar                BOOLEAN     NOT NULL DEFAULT FALSE,
    estado                TEXT        NOT NULL DEFAULT 'PROGRAMADA'
                            CHECK (estado IN ('PROGRAMADA','EN_CURSO','FINALIZADA','CANCELADA')),
    bienvenida_msg        TEXT,
    participantes_max     INTEGER     DEFAULT 50,
    bbb_create_response   JSONB,      -- respuesta XML→JSON del BBB createMeeting
    ref                   UUID,
    row_version           INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

-- Grabaciones de reuniones BBB
CREATE TABLE IF NOT EXISTS ades_bbb_grabaciones (
    id               UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    reunion_id       UUID    NOT NULL REFERENCES ades_bbb_reuniones(id) ON DELETE CASCADE,
    record_id        TEXT    NOT NULL UNIQUE,   -- BBB recording ID
    nombre           TEXT,
    url_playback     TEXT,                      -- URL del reproductor BBB
    duracion_segundos INTEGER,
    tamanio_bytes    BIGINT,
    formatos         JSONB   DEFAULT '[]',      -- [{format:"presentation", url:...}, ...]
    publicada        BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_grabacion  TIMESTAMPTZ,
    ref              UUID,
    row_version      INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

-- Registro de asistencia a reuniones (webhook BBB o self-report)
CREATE TABLE IF NOT EXISTS ades_bbb_asistencia (
    id             UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    reunion_id     UUID    NOT NULL REFERENCES ades_bbb_reuniones(id) ON DELETE CASCADE,
    persona_id     UUID    NOT NULL REFERENCES ades_personas(id),
    rol_bbb        TEXT    NOT NULL DEFAULT 'ASISTENTE'
                      CHECK (rol_bbb IN ('MODERADOR','ASISTENTE')),
    joined_at      TIMESTAMPTZ,
    left_at        TIMESTAMPTZ,
    duracion_segundos INTEGER,
    ref            UUID,
    row_version    INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT,
    UNIQUE (reunion_id, persona_id)
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_bbb_reuniones_grupo       ON ades_bbb_reuniones(grupo_id);
CREATE INDEX IF NOT EXISTS idx_bbb_reuniones_plantel     ON ades_bbb_reuniones(plantel_id);
CREATE INDEX IF NOT EXISTS idx_bbb_reuniones_fecha       ON ades_bbb_reuniones(fecha_programada);
CREATE INDEX IF NOT EXISTS idx_bbb_reuniones_estado      ON ades_bbb_reuniones(estado);
CREATE INDEX IF NOT EXISTS idx_bbb_asistencia_persona    ON ades_bbb_asistencia(persona_id);
CREATE INDEX IF NOT EXISTS idx_bbb_grabaciones_reunion   ON ades_bbb_grabaciones(reunion_id);

-- Triggers de auditoría
SELECT auditoria.asignar_biu('public.ades_bbb_reuniones');
SELECT auditoria.asignar_biu('public.ades_bbb_grabaciones');
SELECT auditoria.asignar_biu('public.ades_bbb_asistencia');

COMMENT ON TABLE ades_bbb_reuniones   IS 'Videoconferencias BBB: CLASE, TUTORIA, REUNION_PADRES, etc.';
COMMENT ON TABLE ades_bbb_grabaciones IS 'Grabaciones de sesiones BBB con URL de reproducción';
COMMENT ON TABLE ades_bbb_asistencia  IS 'Registro de asistencia a videoconferencias BBB';
