-- =============================================================================
-- Migración: 081_h5p.sql
-- Descripción: Crea las tablas para el módulo H5P de contenido educativo
--              interactivo: tipos de contenido, biblioteca de paquetes H5P,
--              asignaciones a grupos/tareas y resultados xAPI por alumno.
-- Tablas afectadas: ades_h5p_tipos, ades_h5p_contenidos, ades_h5p_asignaciones,
--                   ades_h5p_resultados
-- Dependencias: ades_planteles, ades_niveles_educativos, ades_grados,
--               ades_personas, ades_tareas, ades_grupos, ades_estudiantes
-- Autor: ADES
-- Fecha: 2026-06
-- =============================================================================

-- =============================================================================
-- MIGRACIÓN 081 — FASE 25: H5P Contenido Educativo Interactivo
-- =============================================================================

-- Catálogo de tipos de contenido H5P soportados
CREATE TABLE IF NOT EXISTS ades_h5p_tipos (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    clave         TEXT        NOT NULL UNIQUE,   -- e.g. H5P.QuestionSet
    nombre        TEXT        NOT NULL,
    descripcion   TEXT,
    icono         TEXT,
    activo        BOOLEAN     NOT NULL DEFAULT TRUE,
    ref           UUID,
    row_version   INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

-- Biblioteca de contenidos H5P subidos por docentes
CREATE TABLE IF NOT EXISTS ades_h5p_contenidos (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    titulo           TEXT        NOT NULL,
    descripcion      TEXT,
    tipo_id          UUID        REFERENCES ades_h5p_tipos(id),
    h5p_content_id   TEXT        UNIQUE,          -- ID interno del servicio H5P
    h5p_library      TEXT,                        -- e.g. H5P.QuestionSet 1.20
    plantel_id       UUID        REFERENCES ades_planteles(id),
    nivel_id         UUID        REFERENCES ades_niveles_educativos(id),
    grado_id         UUID        REFERENCES ades_grados(id),
    creado_por       UUID        REFERENCES ades_personas(id),
    activo           BOOLEAN     NOT NULL DEFAULT TRUE,
    metadatos        JSONB       NOT NULL DEFAULT '{}',  -- {autor, licencia, idioma, palabras_clave}
    ref              UUID,
    row_version      INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

-- Asignaciones de contenido H5P a tareas/actividades
CREATE TABLE IF NOT EXISTS ades_h5p_asignaciones (
    id              UUID    NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    contenido_id    UUID    NOT NULL REFERENCES ades_h5p_contenidos(id) ON DELETE CASCADE,
    tarea_id        UUID    REFERENCES ades_tareas(id) ON DELETE SET NULL,
    grupo_id        UUID    REFERENCES ades_grupos(id),
    fecha_desde     DATE,
    fecha_hasta     DATE,
    intentos_max    INTEGER DEFAULT 3,
    puntaje_minimo  NUMERIC(5,2) DEFAULT 60.0,  -- % mínimo para aprobar
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    ref             UUID,
    row_version     INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT
);

-- Resultados xAPI por alumno
CREATE TABLE IF NOT EXISTS ades_h5p_resultados (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    contenido_id          UUID        NOT NULL REFERENCES ades_h5p_contenidos(id) ON DELETE CASCADE,
    estudiante_id         UUID        NOT NULL REFERENCES ades_estudiantes(id),
    asignacion_id         UUID        REFERENCES ades_h5p_asignaciones(id),
    intento               INTEGER     NOT NULL DEFAULT 1,
    score_raw             NUMERIC(8,2),
    score_max             NUMERIC(8,2),
    score_escalado        NUMERIC(5,4),           -- 0.0 a 1.0
    completado            BOOLEAN     NOT NULL DEFAULT FALSE,
    aprobado              BOOLEAN,
    tiempo_segundos       INTEGER,
    respuestas_detalle    JSONB       DEFAULT '[]',  -- array de {interaccion, correcto, respuesta_usuario}
    xapi_statement        JSONB,                  -- statement completo xAPI
    ref                   UUID,
    row_version           INTEGER,
    fecha_creacion       TIMESTAMPTZ,
    fecha_modificacion   TIMESTAMPTZ,
    usuario_creacion     TEXT,
    usuario_modificacion TEXT,
    UNIQUE (contenido_id, estudiante_id, intento)
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_h5p_contenidos_plantel  ON ades_h5p_contenidos(plantel_id);
CREATE INDEX IF NOT EXISTS idx_h5p_contenidos_grado    ON ades_h5p_contenidos(grado_id);
CREATE INDEX IF NOT EXISTS idx_h5p_resultados_estudiante ON ades_h5p_resultados(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_h5p_resultados_contenido  ON ades_h5p_resultados(contenido_id);
CREATE INDEX IF NOT EXISTS idx_h5p_asignaciones_grupo    ON ades_h5p_asignaciones(grupo_id);

-- Triggers de auditoría
SELECT auditoria.asignar_biu('public.ades_h5p_tipos');
SELECT auditoria.asignar_biu('public.ades_h5p_contenidos');
SELECT auditoria.asignar_biu('public.ades_h5p_asignaciones');
SELECT auditoria.asignar_biu('public.ades_h5p_resultados');

-- Seeds — tipos de contenido H5P comunes
INSERT INTO ades_h5p_tipos (clave, nombre, descripcion, icono) VALUES
('H5P.QuestionSet',         'Cuestionario',            'Serie de preguntas de opción múltiple con retroalimentación', 'pi pi-question-circle'),
('H5P.InteractiveVideo',    'Video Interactivo',       'Video con preguntas, marcadores y navegación interactiva',    'pi pi-video'),
('H5P.CoursePresentation',  'Presentación de Curso',   'Diapositivas interactivas con preguntas embebidas',          'pi pi-desktop'),
('H5P.DragQuestion',        'Arrastrar y Soltar',      'Ordenar o clasificar conceptos arrastrando elementos',       'pi pi-arrows-alt'),
('H5P.Flashcards',          'Tarjetas de Memoria',     'Tarjetas de estudio con frente y reverso',                   'pi pi-clone'),
('H5P.Summary',             'Resumen Interactivo',     'Lista de afirmaciones correctas/incorrectas para repasar',   'pi pi-list'),
('H5P.SingleChoiceSet',     'Opción Única Rápida',     'Pregunta de opción única con animación inmediata',           'pi pi-check-circle'),
('H5P.TrueFalse',           'Verdadero o Falso',       'Pregunta de verdadero/falso con retroalimentación',          'pi pi-check-square'),
('H5P.Blanks',              'Completar Espacios',      'Rellenar huecos en texto con palabras clave',                'pi pi-pencil'),
('H5P.MarkTheWords',        'Marcar Palabras',         'Identificar palabras específicas en un texto',               'pi pi-tag')
ON CONFLICT (clave) DO NOTHING;

COMMENT ON TABLE ades_h5p_tipos       IS 'Catálogo de tipos de contenido H5P soportados';
COMMENT ON TABLE ades_h5p_contenidos  IS 'Biblioteca de paquetes H5P subidos por docentes';
COMMENT ON TABLE ades_h5p_asignaciones IS 'Asignación de contenido H5P a grupos/tareas con fechas y límites';
COMMENT ON TABLE ades_h5p_resultados  IS 'Resultados xAPI por alumno — integra con ades_tareas';
