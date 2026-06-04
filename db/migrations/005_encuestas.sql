-- =============================================================================
-- Migración 005 — Encuestas y sondeos escolares
-- Ejecutar: docker compose exec -T postgres psql -U ades_admin -d ades \
--   -f /docker-entrypoint-initdb.d/migrations/005_encuestas.sql
-- =============================================================================

-- ── 1. Encuestas ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_encuestas (
    id                  uuid        PRIMARY KEY DEFAULT uuidv7(),
    titulo              varchar(255) NOT NULL,
    descripcion         text,
    tipo                varchar(30) NOT NULL DEFAULT 'SATISFACCION',
    -- SATISFACCION | DIAGNOSTICO | CLIMA_ESCOLAR | EVALUACION_DOCENTE | SALIDA | PERSONALIZADA
    plantel_id          uuid        REFERENCES ades_planteles(id),
    nivel_educativo_id  uuid        REFERENCES ades_niveles_educativos(id),
    grupo_id            uuid        REFERENCES ades_grupos(id),
    audiencia           varchar(20) NOT NULL DEFAULT 'ALUMNO',
    -- ALUMNO | PADRE | DOCENTE | TODOS
    fecha_inicio        date,
    fecha_fin           date,
    anonima             boolean     NOT NULL DEFAULT FALSE,
    activa              boolean     NOT NULL DEFAULT TRUE,
    creado_por_id       uuid        REFERENCES ades_usuarios(id),
    ref                 uuid        NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active           boolean     NOT NULL DEFAULT TRUE,
    fccreacion          timestamptz NOT NULL DEFAULT NOW(),
    fcmodificacion      timestamptz NOT NULL DEFAULT NOW(),
    usuario_creacion    varchar(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion varchar(150) NOT NULL DEFAULT CURRENT_USER,
    row_version         integer     NOT NULL DEFAULT 1
);

CREATE TRIGGER trg_aud_biu_enc
    BEFORE INSERT OR UPDATE ON ades_encuestas
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

COMMENT ON TABLE ades_encuestas IS 'Encuestas y sondeos escolares (satisfacción, diagnóstico, clima)';

-- ── 2. Preguntas ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ades_encuesta_preguntas (
    id              uuid        PRIMARY KEY DEFAULT uuidv7(),
    encuesta_id     uuid        NOT NULL REFERENCES ades_encuestas(id) ON DELETE CASCADE,
    texto           varchar(500) NOT NULL,
    tipo_pregunta   varchar(30) NOT NULL DEFAULT 'ESCALA_5',
    -- ESCALA_5 | OPCION_MULTIPLE | TEXTO_LIBRE | BOOLEANO
    opciones        jsonb,
    -- Para OPCION_MULTIPLE: ["Muy satisfecho", "Satisfecho", "Regular", "Insatisfecho"]
    orden           integer     NOT NULL DEFAULT 1,
    obligatoria     boolean     NOT NULL DEFAULT TRUE,
    ref             uuid        NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active       boolean     NOT NULL DEFAULT TRUE,
    fccreacion      timestamptz NOT NULL DEFAULT NOW(),
    fcmodificacion  timestamptz NOT NULL DEFAULT NOW(),
    usuario_creacion    varchar(150) NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion varchar(150) NOT NULL DEFAULT CURRENT_USER,
    row_version     integer     NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_ep_encuesta ON ades_encuesta_preguntas (encuesta_id, orden)
    WHERE is_active = TRUE;

CREATE TRIGGER trg_aud_biu_encq
    BEFORE INSERT OR UPDATE ON ades_encuesta_preguntas
    FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();

-- ── 3. Respuestas ─────────────────────────────────────────────────────────────
-- Sin trigger completo de auditoría para no comprometer encuestas anónimas.
CREATE TABLE IF NOT EXISTS ades_encuesta_respuestas (
    id                   uuid        PRIMARY KEY DEFAULT uuidv7(),
    encuesta_id          uuid        NOT NULL REFERENCES ades_encuestas(id),
    pregunta_id          uuid        NOT NULL REFERENCES ades_encuesta_preguntas(id),
    respondido_por_id    uuid        REFERENCES ades_usuarios(id),
    sesion_id            varchar(50) NOT NULL,
    -- UUID generado en cliente; agrupa todas las respuestas de una sesión
    texto_respuesta      text,
    valor_numerico       numeric(5,2),   -- ESCALA_5: 1.0–5.0
    opcion_seleccionada  varchar(255),   -- OPCION_MULTIPLE: texto de la opción elegida
    fccreacion           timestamptz NOT NULL DEFAULT NOW(),
    UNIQUE (pregunta_id, sesion_id)
);

CREATE INDEX IF NOT EXISTS idx_er_encuesta  ON ades_encuesta_respuestas (encuesta_id);
CREATE INDEX IF NOT EXISTS idx_er_pregunta  ON ades_encuesta_respuestas (pregunta_id);
CREATE INDEX IF NOT EXISTS idx_er_sesion    ON ades_encuesta_respuestas (sesion_id);

COMMENT ON COLUMN ades_encuesta_respuestas.sesion_id
    IS 'UUID generado en cliente; permite agrupar respuestas de una misma persona por encuesta';

-- ── 4. Seeds de ejemplo ───────────────────────────────────────────────────────
-- Encuesta de clima escolar (global, no vinculada a plantel específico)
INSERT INTO ades_encuestas (titulo, descripcion, tipo, audiencia, anonima)
SELECT 'Clima Escolar 2026-2027',
       'Sondeo semestral para evaluar el ambiente de aprendizaje y convivencia escolar.',
       'CLIMA_ESCOLAR', 'ALUMNO', TRUE
WHERE NOT EXISTS (SELECT 1 FROM ades_encuestas WHERE titulo = 'Clima Escolar 2026-2027');

-- Preguntas para la encuesta de clima
WITH enc AS (SELECT id FROM ades_encuestas WHERE titulo = 'Clima Escolar 2026-2027' LIMIT 1)
INSERT INTO ades_encuesta_preguntas (encuesta_id, texto, tipo_pregunta, orden)
SELECT enc.id, p.texto, p.tipo, p.orden FROM enc, (VALUES
    ('¿Cómo calificarías el ambiente general del salón?', 'ESCALA_5', 1),
    ('¿Te sientes seguro/a en la escuela?', 'BOOLEANO', 2),
    ('¿Con qué frecuencia participas en clase?',
     'OPCION_MULTIPLE', 3),
    ('¿Qué mejorarías de tu escuela?', 'TEXTO_LIBRE', 4)
) AS p(texto, tipo, orden)
WHERE NOT EXISTS (
    SELECT 1 FROM ades_encuesta_preguntas WHERE encuesta_id = enc.id
);

-- Opciones para la pregunta de participación
UPDATE ades_encuesta_preguntas
SET opciones = '["Siempre", "Frecuentemente", "A veces", "Casi nunca", "Nunca"]'::jsonb
WHERE texto = '¿Con qué frecuencia participas en clase?'
  AND opciones IS NULL;
