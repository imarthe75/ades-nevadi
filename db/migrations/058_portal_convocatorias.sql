-- ============================================================
-- MIGRACIÓN 058 — PORTAL PÚBLICO DE CONVOCATORIAS
-- Portal externo portalnvd.setag.mx para postulaciones de
-- vacantes, inscripciones, becas, etc.
-- Cumplimiento LFPDPPP (derechos ARCO).
-- ============================================================
-- Ejecutar:
--   docker compose exec -T postgres psql -U ades_admin -d ades < db/migrations/058_portal_convocatorias.sql

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 0. Schema dedicado
-- ─────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS portal;

COMMENT ON SCHEMA portal IS
  'Portal público de convocatorias Instituto Nevadi (portalnvd.setag.mx).
   Usuarios externos, postulaciones, documentos y ARCO.';

-- ─────────────────────────────────────────────────────────────
-- 1. Tipos enumerados
-- ─────────────────────────────────────────────────────────────
DO $$ BEGIN
  CREATE TYPE portal.tipo_convocatoria AS ENUM (
    'INSCRIPCION',
    'REINSCRIPCION',
    'VACANTE_DOCENTE',
    'VACANTE_ADMINISTRATIVA',
    'BECA',
    'INTERCAMBIO',
    'EXTRACURRICULAR'
  );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE portal.estado_postulacion AS ENUM (
    'BORRADOR',
    'ENVIADA',
    'EN_REVISION',
    'PRESELECCIONADA',
    'ACEPTADA',
    'RECHAZADA',
    'LISTA_ESPERA'
  );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE portal.tipo_solicitud_arco AS ENUM (
    'ACCESO',
    'RECTIFICACION',
    'CANCELACION',
    'OPOSICION'
  );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE portal.estado_solicitud_arco AS ENUM (
    'RECIBIDA',
    'EN_PROCESO',
    'RESUELTA',
    'IMPROCEDENTE'
  );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ─────────────────────────────────────────────────────────────
-- 2. portal.convocatorias
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal.convocatorias (
  id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  tipo                     portal.tipo_convocatoria NOT NULL,
  titulo                   TEXT         NOT NULL,
  descripcion              TEXT,
  requisitos_generales     TEXT,                    -- lista libre de requisitos generales
  plantel_id               UUID         REFERENCES public.ades_planteles(id),  -- NULL = global
  nivel_educativo_id       UUID         REFERENCES public.ades_niveles_educativos(id), -- NULL = todos
  fecha_publicacion        TIMESTAMPTZ,
  fecha_inicio_postulacion TIMESTAMPTZ  NOT NULL,
  fecha_cierre_postulacion TIMESTAMPTZ  NOT NULL,
  cupo_maximo              INTEGER,                 -- NULL = sin límite
  cupo_actual              INTEGER      NOT NULL DEFAULT 0,
  imagen_url               TEXT,
  aviso_privacidad_version TEXT         NOT NULL DEFAULT '1.0',
  is_published             BOOLEAN      NOT NULL DEFAULT FALSE,
  is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
  -- auditoría ADES estándar
  ref                      UUID,
  row_version              INTEGER,
  fecha_creacion           TIMESTAMPTZ,
  fecha_modificacion       TIMESTAMPTZ,
  usuario_creacion         TEXT,
  usuario_modificacion     TEXT,

  CONSTRAINT ck_cupo_positivo CHECK (cupo_maximo IS NULL OR cupo_maximo > 0),
  CONSTRAINT ck_fechas_convocatoria CHECK (fecha_cierre_postulacion > fecha_inicio_postulacion)
);

COMMENT ON TABLE portal.convocatorias IS 'Convocatorias publicadas en el portal externo portalnvd.setag.mx';
COMMENT ON COLUMN portal.convocatorias.plantel_id IS 'NULL = convocatoria global (todos los planteles)';
COMMENT ON COLUMN portal.convocatorias.cupo_maximo IS 'NULL = sin límite de postulaciones';

-- ─────────────────────────────────────────────────────────────
-- 3. portal.requisitos_documentos
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal.requisitos_documentos (
  id                    UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  convocatoria_id       UUID    NOT NULL REFERENCES portal.convocatorias(id) ON DELETE CASCADE,
  nombre                TEXT    NOT NULL,
  descripcion           TEXT,
  es_obligatorio        BOOLEAN NOT NULL DEFAULT TRUE,
  tipos_mime_permitidos TEXT[]  NOT NULL DEFAULT ARRAY['application/pdf'],
  tamano_maximo_mb      INTEGER NOT NULL DEFAULT 5,
  orden                 INTEGER NOT NULL DEFAULT 0,
  is_active             BOOLEAN NOT NULL DEFAULT TRUE,
  -- auditoría
  ref                      UUID,
  row_version              INTEGER,
  fecha_creacion           TIMESTAMPTZ,
  fecha_modificacion       TIMESTAMPTZ,
  usuario_creacion         TEXT,
  usuario_modificacion     TEXT
);

COMMENT ON TABLE portal.requisitos_documentos IS 'Documentos requeridos por cada convocatoria';

-- ─────────────────────────────────────────────────────────────
-- 4. portal.usuarios  (postulantes externos — NO son ades_personas)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal.usuarios (
  id                       UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  email                    TEXT    NOT NULL UNIQUE,
  password_hash            TEXT    NOT NULL,
  nombre_completo          TEXT    NOT NULL,
  telefono                 TEXT,
  fecha_nacimiento         DATE,
  is_email_verified        BOOLEAN NOT NULL DEFAULT FALSE,
  token_verificacion       TEXT,
  token_recuperacion       TEXT,
  token_expira_en          TIMESTAMPTZ,
  consentimiento_privacidad  BOOLEAN NOT NULL DEFAULT FALSE,
  consentimiento_fecha       TIMESTAMPTZ,
  consentimiento_version     TEXT,
  fecha_ultimo_acceso      TIMESTAMPTZ,
  is_active                BOOLEAN NOT NULL DEFAULT TRUE,
  -- auditoría
  ref                      UUID,
  row_version              INTEGER,
  fecha_creacion           TIMESTAMPTZ,
  fecha_modificacion       TIMESTAMPTZ,
  usuario_creacion         TEXT,
  usuario_modificacion     TEXT
);

COMMENT ON TABLE portal.usuarios IS
  'Cuentas de postulantes externos del portal de convocatorias.
   Son independientes de los usuarios internos ADES (ades_usuarios).
   Datos personales sujetos a LFPDPPP — derechos ARCO.';

-- ─────────────────────────────────────────────────────────────
-- 5. Secuencia y función de folio
-- ─────────────────────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS portal.folio_seq START 1;

CREATE OR REPLACE FUNCTION portal.generar_folio(p_tipo portal.tipo_convocatoria)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
  v_prefijo TEXT;
  v_año     TEXT := EXTRACT(YEAR FROM CURRENT_DATE)::TEXT;
  v_seq     TEXT := LPAD(nextval('portal.folio_seq')::TEXT, 5, '0');
BEGIN
  v_prefijo := CASE p_tipo
    WHEN 'INSCRIPCION'           THEN 'INSC'
    WHEN 'REINSCRIPCION'         THEN 'REIN'
    WHEN 'VACANTE_DOCENTE'       THEN 'VACD'
    WHEN 'VACANTE_ADMINISTRATIVA' THEN 'VACA'
    WHEN 'BECA'                  THEN 'BECA'
    WHEN 'INTERCAMBIO'           THEN 'INTC'
    WHEN 'EXTRACURRICULAR'       THEN 'EXTC'
    ELSE                              'GENE'
  END;
  RETURN 'NVD-' || v_prefijo || '-' || v_año || '-' || v_seq;
END;
$$;

COMMENT ON FUNCTION portal.generar_folio IS
  'Genera folio único por postulación. Formato: NVD-INSC-2026-00042.
   Usar en INSERT de portal.postulaciones.';

-- ─────────────────────────────────────────────────────────────
-- 6. portal.postulaciones
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal.postulaciones (
  id                        UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
  convocatoria_id           UUID                     NOT NULL REFERENCES portal.convocatorias(id),
  usuario_id                UUID                     NOT NULL REFERENCES portal.usuarios(id),
  folio                     TEXT                     NOT NULL UNIQUE,
  estado                    portal.estado_postulacion NOT NULL DEFAULT 'BORRADOR',
  datos_adicionales         JSONB,                   -- campos variables según tipo de convocatoria
  consentimiento_privacidad BOOLEAN                  NOT NULL DEFAULT FALSE,
  ip_postulacion            INET,
  fecha_envio               TIMESTAMPTZ,
  observaciones_admin       TEXT,                    -- solo visible internamente
  is_active                 BOOLEAN                  NOT NULL DEFAULT TRUE,
  -- auditoría
  ref                      UUID,
  row_version              INTEGER,
  fecha_creacion           TIMESTAMPTZ,
  fecha_modificacion       TIMESTAMPTZ,
  usuario_creacion         TEXT,
  usuario_modificacion     TEXT,

  CONSTRAINT uq_usuario_convocatoria UNIQUE (convocatoria_id, usuario_id)
);

COMMENT ON TABLE portal.postulaciones IS 'Una postulación por usuario por convocatoria';
COMMENT ON COLUMN portal.postulaciones.folio IS 'Código público de seguimiento, ej: NVD-INSC-2026-00042';
COMMENT ON COLUMN portal.postulaciones.datos_adicionales IS 'JSONB con campos variables según tipo (CV libre, RFC, CURP, promedio, etc.)';
COMMENT ON COLUMN portal.postulaciones.observaciones_admin IS 'Visibles solo para administradores ADES internos';

-- ─────────────────────────────────────────────────────────────
-- 7. portal.documentos
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal.documentos (
  id               UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
  postulacion_id   UUID    NOT NULL REFERENCES portal.postulaciones(id) ON DELETE CASCADE,
  requisito_id     UUID    REFERENCES portal.requisitos_documentos(id), -- NULL si es doc libre
  tipo_documento   TEXT    NOT NULL,
  nombre_original  TEXT    NOT NULL,
  mimetype         TEXT    NOT NULL,
  tamano_bytes     BIGINT  NOT NULL,
  ruta_minio       TEXT    NOT NULL,
  hash_sha256      TEXT,
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  -- auditoría
  ref                      UUID,
  row_version              INTEGER,
  fecha_creacion           TIMESTAMPTZ,
  fecha_modificacion       TIMESTAMPTZ,
  usuario_creacion         TEXT,
  usuario_modificacion     TEXT
);

COMMENT ON TABLE portal.documentos IS
  'Archivos subidos por postulantes. Almacenados en MinIO bucket portal-convocatorias.
   Acceso solo mediante presigned URLs con expiración de 15 min.';
COMMENT ON COLUMN portal.documentos.ruta_minio IS 'Ruta interna MinIO. Ejemplo: portal-convocatorias/2026/uuid-postulacion/uuid-doc.pdf';

-- ─────────────────────────────────────────────────────────────
-- 8. portal.solicitudes_arco
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal.solicitudes_arco (
  id                     UUID                      PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id             UUID                      REFERENCES portal.usuarios(id), -- NULL si no tiene cuenta
  email_solicitante      TEXT                      NOT NULL,
  nombre_solicitante     TEXT                      NOT NULL,
  tipo                   portal.tipo_solicitud_arco NOT NULL,
  descripcion            TEXT                      NOT NULL,
  estado                 portal.estado_solicitud_arco NOT NULL DEFAULT 'RECIBIDA',
  respuesta_admin        TEXT,
  fecha_limite_respuesta DATE                      NOT NULL, -- 20 días hábiles por LFPDPPP
  fecha_resolucion       TIMESTAMPTZ,
  ip_solicitud           INET,
  -- auditoría
  ref                      UUID,
  row_version              INTEGER,
  fecha_creacion           TIMESTAMPTZ,
  fecha_modificacion       TIMESTAMPTZ,
  usuario_creacion         TEXT,
  usuario_modificacion     TEXT
);

COMMENT ON TABLE portal.solicitudes_arco IS
  'Solicitudes de derechos ARCO (Acceso, Rectificación, Cancelación, Oposición).
   Obligatorio por LFPDPPP. Plazo de respuesta: 20 días hábiles.
   Responsable: privacidad@nevadi.edu.mx';
COMMENT ON COLUMN portal.solicitudes_arco.fecha_limite_respuesta IS
  'Fecha límite de respuesta = fecha_creacion + 20 días hábiles aprox.';

-- ─────────────────────────────────────────────────────────────
-- 9. Índices
-- ─────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_convocatorias_tipo         ON portal.convocatorias(tipo);
CREATE INDEX IF NOT EXISTS idx_convocatorias_plantel      ON portal.convocatorias(plantel_id) WHERE plantel_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_convocatorias_publicada    ON portal.convocatorias(is_published, is_active, fecha_cierre_postulacion);
CREATE INDEX IF NOT EXISTS idx_postulaciones_usuario      ON portal.postulaciones(usuario_id);
CREATE INDEX IF NOT EXISTS idx_postulaciones_convocatoria ON portal.postulaciones(convocatoria_id);
CREATE INDEX IF NOT EXISTS idx_postulaciones_folio        ON portal.postulaciones(folio);
CREATE INDEX IF NOT EXISTS idx_postulaciones_estado       ON portal.postulaciones(estado);
CREATE INDEX IF NOT EXISTS idx_documentos_postulacion     ON portal.documentos(postulacion_id);
CREATE INDEX IF NOT EXISTS idx_usuarios_email             ON portal.usuarios(email);
CREATE INDEX IF NOT EXISTS idx_arco_estado               ON portal.solicitudes_arco(estado);
CREATE INDEX IF NOT EXISTS idx_arco_usuario              ON portal.solicitudes_arco(usuario_id) WHERE usuario_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- 10. Triggers de auditoría (audit_biu — para DEV)
-- ─────────────────────────────────────────────────────────────
SELECT auditoria.asignar_biu('portal.convocatorias');
SELECT auditoria.asignar_biu('portal.requisitos_documentos');
SELECT auditoria.asignar_biu('portal.usuarios');
SELECT auditoria.asignar_biu('portal.postulaciones');
SELECT auditoria.asignar_biu('portal.documentos');
SELECT auditoria.asignar_biu('portal.solicitudes_arco');

COMMIT;
