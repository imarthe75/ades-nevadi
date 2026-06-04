-- =============================================================================
-- ADES — Migración 002: FASE 3 complemento + FASE 4 base
-- Tablas nuevas:
--   - ades_evaluacion_docente (indicadores, periodos, resultados)
--   - ades_criterios_eval_docente
--   - ades_ai_conversaciones  (historial asistente IA)
--   - ades_alertas_academicas (riesgo detectado)
-- =============================================================================

-- =============================================================================
-- FASE 3 — EVALUACIÓN DOCENTE 360°
-- Indicadores: puntualidad, dominio de materia, planeación, clima de aula,
--              atención a alumnos con riesgo, evaluación formativa.
-- Evaluadores: director, coordinador académico, pares, auto-evaluación.
-- =============================================================================

CREATE TABLE ades_criterios_eval_docente (
    id                   UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    nombre_criterio      VARCHAR(120) NOT NULL,
    descripcion          TEXT,
    categoria            VARCHAR(60)  NOT NULL, -- PEDAGOGICO, ADMINISTRATIVO, ACTITUDINAL, RESULTADOS
    peso_porcentual      NUMERIC(5,2) NOT NULL DEFAULT 100.0,
    escala_min           SMALLINT    NOT NULL DEFAULT 1,
    escala_max           SMALLINT    NOT NULL DEFAULT 5,
    nivel_educativo_id   UUID         REFERENCES ades_niveles_educativos(id),
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    ref                  UUID        NOT NULL DEFAULT uuidv7() UNIQUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_criterios_eval_docente IS 'Catálogo de criterios para evaluación docente. Configurable por nivel educativo.';
SELECT auditoria.asignar_trigger('ades_criterios_eval_docente');

CREATE TABLE ades_evaluacion_docente (
    id                   UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    profesor_id          UUID         NOT NULL REFERENCES ades_profesores(id),
    ciclo_escolar_id     UUID         NOT NULL REFERENCES ades_ciclos_escolares(id),
    evaluador_id         UUID         NOT NULL REFERENCES ades_usuarios(id),
    tipo_evaluador       VARCHAR(30)  NOT NULL, -- DIRECTOR, COORDINADOR, PAR, AUTO
    fecha_evaluacion     DATE        NOT NULL DEFAULT CURRENT_DATE,
    calificacion_global  NUMERIC(5,2),          -- calculado al guardar criterios
    comentarios          TEXT,
    estatus              VARCHAR(20) NOT NULL DEFAULT 'BORRADOR', -- BORRADOR, ENVIADA, APROBADA
    ref                  UUID        NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_evaluacion_docente IS 'Evaluación 360° de un docente por ciclo. Un evaluador puede emitir una por ciclo.';
SELECT auditoria.asignar_trigger('ades_evaluacion_docente');

CREATE TABLE ades_eval_docente_criterios (
    id                    UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    evaluacion_id         UUID         NOT NULL REFERENCES ades_evaluacion_docente(id) ON DELETE CASCADE,
    criterio_id           UUID         NOT NULL REFERENCES ades_criterios_eval_docente(id),
    calificacion          SMALLINT     NOT NULL,
    observacion           TEXT,
    ref                   UUID        NOT NULL DEFAULT uuidv7() UNIQUE,
    fccreacion            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion  VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version           INTEGER     NOT NULL DEFAULT 1,
    CONSTRAINT uq_eval_criterio UNIQUE (evaluacion_id, criterio_id)
);
COMMENT ON TABLE ades_eval_docente_criterios IS 'Calificación por criterio dentro de una evaluación docente.';
SELECT auditoria.asignar_trigger('ades_eval_docente_criterios');

-- Índices evaluación docente
CREATE INDEX idx_eval_docente_prof ON ades_evaluacion_docente(profesor_id, ciclo_escolar_id);
CREATE INDEX idx_eval_docente_evaluador ON ades_evaluacion_docente(evaluador_id);

-- Seed de criterios por defecto
INSERT INTO ades_criterios_eval_docente (nombre_criterio, descripcion, categoria, peso_porcentual) VALUES
  ('Puntualidad y asistencia',    'El docente llega a tiempo y registra asistencia correctamente', 'ADMINISTRATIVO', 15.0),
  ('Dominio de contenidos',       'Demuestra conocimiento sólido de los temas de su materia',      'PEDAGOGICO',     20.0),
  ('Planeación didáctica',        'Entrega planeación al inicio del ciclo y la actualiza',          'PEDAGOGICO',     15.0),
  ('Uso de estrategias activas',  'Aplica metodologías participativas y diferenciadas',             'PEDAGOGICO',     15.0),
  ('Evaluación formativa',        'Retroalimenta a los alumnos de manera oportuna y constructiva', 'PEDAGOGICO',     15.0),
  ('Clima de aula y disciplina',  'Mantiene un ambiente de respeto y orden en el salón',           'ACTITUDINAL',    10.0),
  ('Atención a alumnos en riesgo','Identifica y apoya alumnos con dificultades académicas',        'RESULTADOS',     10.0)
ON CONFLICT DO NOTHING;


-- =============================================================================
-- FASE 4 — IA: HISTORIAL DE CONVERSACIONES CON EL ASISTENTE
-- =============================================================================

CREATE TABLE ades_ai_conversaciones (
    id                   UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    usuario_id           UUID         REFERENCES ades_usuarios(id),
    sesion_id            VARCHAR(80)  NOT NULL,                     -- ID de sesión del chat
    rol                  VARCHAR(20)  NOT NULL,                     -- user | assistant | system
    contenido            TEXT         NOT NULL,
    modelo               VARCHAR(60)  NOT NULL DEFAULT 'claude-sonnet-4-6',
    tokens_entrada       INTEGER,
    tokens_salida        INTEGER,
    contexto             JSONB,                                     -- plantel_id, ciclo_id, etc.
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_ai_conversaciones IS 'Historial de mensajes con el asistente pedagógico IA.';
CREATE INDEX idx_ai_conv_sesion ON ades_ai_conversaciones(sesion_id, fccreacion);
CREATE INDEX idx_ai_conv_usuario ON ades_ai_conversaciones(usuario_id, fccreacion DESC);


-- =============================================================================
-- FASE 4 — ALERTAS ACADÉMICAS (riesgo de reprobación)
-- =============================================================================

CREATE TABLE ades_alertas_academicas (
    id                   UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    estudiante_id        UUID         NOT NULL REFERENCES ades_estudiantes(id),
    grupo_id             UUID         NOT NULL REFERENCES ades_grupos(id),
    tipo_alerta          VARCHAR(40)  NOT NULL, -- RIESGO_REPROBACION, AUSENTISMO, BAJO_RENDIMIENTO
    nivel_riesgo         VARCHAR(20)  NOT NULL, -- BAJO, MEDIO, ALTO, CRITICO
    descripcion          TEXT         NOT NULL,
    datos_calculo        JSONB,                 -- snapshot: promedio, ausencias, materias_riesgo
    generada_por         VARCHAR(30)  NOT NULL DEFAULT 'SISTEMA', -- SISTEMA, IA, MANUAL
    atendida             BOOLEAN     NOT NULL DEFAULT FALSE,
    fecha_atencion       DATE,
    atendida_por_id      UUID         REFERENCES ades_usuarios(id),
    ref                  UUID        NOT NULL DEFAULT uuidv7() UNIQUE,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fcmodificacion       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion     VARCHAR(150) NOT NULL DEFAULT current_user,
    usuario_modificacion VARCHAR(150) NOT NULL DEFAULT current_user,
    row_version          INTEGER     NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_alertas_academicas IS 'Alertas de riesgo académico generadas por el sistema o la IA.';
CREATE INDEX idx_alertas_estudiante ON ades_alertas_academicas(estudiante_id, fccreacion DESC);
CREATE INDEX idx_alertas_grupo ON ades_alertas_academicas(grupo_id, atendida);
SELECT auditoria.asignar_trigger('ades_alertas_academicas');
