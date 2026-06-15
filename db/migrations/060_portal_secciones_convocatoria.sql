-- ============================================================
-- 060 — Secciones de contenido LMS para convocatorias del portal
-- Permite crear páginas descriptivas ricas (tipo Moodle/Canvas)
-- por convocatoria, con secciones tipadas y ordenables.
-- ============================================================

-- Enum de tipos de sección
DO $$ BEGIN
    CREATE TYPE portal.tipo_seccion AS ENUM (
        'INTRO',      -- Introducción / hero destacado
        'ENCABEZADO', -- Título de sección intermedia
        'TEXTO',      -- Bloque de texto libre (HTML sanitizado)
        'LISTA',      -- Lista de puntos (datos JSONB: ["item1","item2",...])
        'PROCESO',    -- Pasos / timeline (datos JSONB: [{num,titulo,desc},...])
        'FAQ',        -- Preguntas frecuentes (datos JSONB: [{pregunta,respuesta},...])
        'VIDEO',      -- Video embebido (datos JSONB: {url,tipo:"youtube"|"vimeo"|"mp4",poster_url?})
        'GALERIA',    -- Galería de imágenes (datos JSONB: [{url,caption,alt},...])
        'CTA',        -- Llamada a la acción / botón destacado (datos JSONB: {texto,url,variante?})
        'AVISO'       -- Cuadro de aviso / alerta (datos JSONB: {nivel:"info"|"warn"|"danger",icono?})
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Tabla principal de secciones
CREATE TABLE IF NOT EXISTS portal.secciones_convocatoria (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    convocatoria_id     UUID        NOT NULL,
    tipo_seccion        portal.tipo_seccion NOT NULL,
    titulo              TEXT,
    contenido           TEXT,           -- HTML sanitizado o Markdown
    datos               JSONB,          -- Payload estructurado según tipo_seccion
    orden               INTEGER     NOT NULL DEFAULT 0,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    -- auditoría
    ref                 UUID,
    row_version         INTEGER     NOT NULL DEFAULT 1,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion    TEXT,
    usuario_modificacion TEXT,

    CONSTRAINT pk_secciones_convocatoria PRIMARY KEY (id),
    CONSTRAINT fk_secciones_convocatoria FOREIGN KEY (convocatoria_id)
        REFERENCES portal.convocatorias(id) ON DELETE CASCADE
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_secciones_conv_id
    ON portal.secciones_convocatoria (convocatoria_id, is_active, orden);

-- Trigger de auditoría
SELECT auditoria.asignar_biu('portal.secciones_convocatoria');

-- Comentarios
COMMENT ON TABLE  portal.secciones_convocatoria IS 'Secciones de contenido LMS por convocatoria (tipo Moodle)';
COMMENT ON COLUMN portal.secciones_convocatoria.tipo_seccion IS 'Tipo de bloque visual: INTRO,TEXTO,LISTA,PROCESO,FAQ,VIDEO,GALERIA,CTA,AVISO';
COMMENT ON COLUMN portal.secciones_convocatoria.contenido    IS 'Texto libre HTML sanitizado, usado en TEXTO/INTRO/ENCABEZADO';
COMMENT ON COLUMN portal.secciones_convocatoria.datos        IS 'Payload JSONB estructurado según tipo_seccion';
COMMENT ON COLUMN portal.secciones_convocatoria.orden        IS 'Posición relativa (0 = primero); actualizar con endpoint reordenar';
