-- =============================================================================
-- MIGRACION 123: Tablas base de Learning Paths (nunca se crearon)
-- =============================================================================
-- Objetivo: mx.ades.modules.learning_paths.infrastructure.outbound.persistence
--           .LearningPathPersistenceAdapter y las migraciones 028/092 asumen que
--           ades_learning_paths, ades_lp_recursos, ades_lp_asignaciones y
--           ades_lp_progreso ya existen (usan ALTER TABLE / INSERT), pero
--           ninguna migración las creó — la migración original se perdió.
--           028 y 092 quedan como no-ops seguros (ADD COLUMN IF NOT EXISTS)
--           sobre el esquema ya completo definido aquí.
-- Fecha: 2026-07-10
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS ades_learning_paths (
    id                    UUID NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    nombre                VARCHAR(200) NOT NULL,
    descripcion           TEXT,
    nivel_educativo_id    UUID REFERENCES ades_niveles_educativos(id),
    materia_id            UUID REFERENCES ades_materias(id),
    criterio_activacion   VARCHAR(50),
    umbral_activacion     NUMERIC(5,2),
    ref                   UUID NOT NULL DEFAULT uuidv7(),
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      TEXT NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion  TEXT NOT NULL DEFAULT CURRENT_USER,
    row_version           INTEGER NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_learning_paths IS 'Rutas de aprendizaje adaptativo (learning paths) — CU ajuste dinámico';

CREATE TABLE IF NOT EXISTS ades_lp_recursos (
    id                    UUID NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    path_id               UUID NOT NULL REFERENCES ades_learning_paths(id) ON DELETE CASCADE,
    orden                 INTEGER NOT NULL DEFAULT 0,
    tipo                  VARCHAR(30),
    titulo                VARCHAR(200) NOT NULL,
    descripcion           TEXT,
    url_recurso           VARCHAR(500),
    duracion_min          INTEGER,
    obligatorio           BOOLEAN NOT NULL DEFAULT FALSE,
    ref                   UUID NOT NULL DEFAULT uuidv7(),
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      TEXT NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion  TEXT NOT NULL DEFAULT CURRENT_USER,
    row_version           INTEGER NOT NULL DEFAULT 1
);
COMMENT ON TABLE ades_lp_recursos IS 'Recursos (contenido) de cada learning path, en orden';

CREATE TABLE IF NOT EXISTS ades_lp_asignaciones (
    id                    UUID NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    path_id               UUID NOT NULL REFERENCES ades_learning_paths(id) ON DELETE CASCADE,
    estudiante_id         UUID NOT NULL REFERENCES ades_estudiantes(id) ON DELETE CASCADE,
    asignado_por          UUID REFERENCES ades_usuarios(id),
    motivo                TEXT,
    estatus               VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    pct_completado        NUMERIC(5,2) NOT NULL DEFAULT 0,
    fcinicio              TIMESTAMPTZ,
    fccompletado          TIMESTAMPTZ,
    ia_recomendacion      JSONB DEFAULT NULL,
    ref                   UUID NOT NULL DEFAULT uuidv7(),
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      TEXT NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion  TEXT NOT NULL DEFAULT CURRENT_USER,
    row_version           INTEGER NOT NULL DEFAULT 1,
    UNIQUE (path_id, estudiante_id)
);
COMMENT ON TABLE ades_lp_asignaciones IS 'Asignación de un learning path a un estudiante + progreso agregado';
COMMENT ON COLUMN ades_lp_asignaciones.ia_recomendacion
  IS 'JSON con análisis Claude: {resumen, fortalezas, areas_mejora, estrategias, recursos_priorizados}';

CREATE TABLE IF NOT EXISTS ades_lp_progreso (
    id                    UUID NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    asignacion_id         UUID NOT NULL REFERENCES ades_lp_asignaciones(id) ON DELETE CASCADE,
    recurso_id            UUID NOT NULL REFERENCES ades_lp_recursos(id) ON DELETE CASCADE,
    completado            BOOLEAN NOT NULL DEFAULT FALSE,
    tiempo_min            INTEGER,
    calificacion          NUMERIC(5,2),
    fccompletado          TIMESTAMPTZ,
    ref                   UUID NOT NULL DEFAULT uuidv7(),
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_modificacion    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    usuario_creacion      TEXT NOT NULL DEFAULT CURRENT_USER,
    usuario_modificacion  TEXT NOT NULL DEFAULT CURRENT_USER,
    row_version           INTEGER NOT NULL DEFAULT 1,
    UNIQUE (asignacion_id, recurso_id)
);
COMMENT ON TABLE ades_lp_progreso IS 'Progreso de un estudiante por recurso dentro de su asignación de learning path';

CREATE INDEX IF NOT EXISTS idx_lp_recursos_path ON ades_lp_recursos(path_id);
CREATE INDEX IF NOT EXISTS idx_lp_asignaciones_estudiante ON ades_lp_asignaciones(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_lp_progreso_asignacion ON ades_lp_progreso(asignacion_id);

-- ades_lp_recursos y ades_lp_asignaciones NO se registran aquí con asignar_biu():
-- la migración 028 les crea su propio trigger con nombre distinto
-- (trg_lp_recursos_biu / trg_lp_asignaciones_biu) — evita doble trigger.
SELECT auditoria.asignar_biu('public.ades_learning_paths');
SELECT auditoria.asignar_biu('public.ades_lp_progreso');

COMMIT;
