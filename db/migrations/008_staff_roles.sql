-- =============================================================================
-- Migración 008 — Ampliación de estructura de personal
--
-- Cambios:
--   1. Nuevos roles: TUTOR, APOYO_ADMINISTRATIVO, APOYO_ACADEMICO, COORDINADOR_AREA
--   2. Actualizar descripción de DIRECTOR (puede ser por nivel educativo dentro del plantel)
--   3. Tabla ades_areas_academicas — áreas globales (Matemáticas, Español, Inglés, etc.)
--   4. Tabla ades_coordinaciones_area — asignar COORDINADOR_AREA a un área global
--   5. Eliminar regla de negocio "un solo profesor de inglés por plantel" de la BD
--
-- Regla eliminada: No hay límite al número de profesores de inglés por plantel.
--                  Un plantel puede tener múltiples docentes de la misma materia.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Nuevos roles de personal
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_roles (nombre_rol, descripcion, nivel_acceso)
VALUES
    ('TUTOR',                 'Tutor académico — seguimiento personalizado de un grupo de estudiantes', 3),
    ('APOYO_ADMINISTRATIVO',  'Personal de apoyo administrativo — trámites, archivo, atención',        4),
    ('APOYO_ACADEMICO',       'Personal de apoyo académico — recursos, biblioteca, laboratorio',       4),
    ('COORDINADOR_AREA',      'Coordinador de área académica global — Matemáticas, Español, Inglés, etc.', 2)
ON CONFLICT (nombre_rol) DO UPDATE
    SET descripcion = EXCLUDED.descripcion,
        nivel_acceso = EXCLUDED.nivel_acceso;

-- Actualizar descripción de DIRECTOR para reflejar que puede ser por nivel dentro del plantel
UPDATE ades_roles
SET descripcion = 'Director de plantel o de nivel educativo dentro de un plantel (Primaria/Secundaria/Preparatoria)'
WHERE nombre_rol = 'DIRECTOR';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Tabla de áreas académicas globales
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_areas_academicas (
    id          UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    ref         UUID        NOT NULL UNIQUE DEFAULT uuidv7(),
    nombre      VARCHAR(80) NOT NULL UNIQUE,
    descripcion TEXT,
    color       VARCHAR(7)  DEFAULT '#6366F1',
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    fccreacion          TIMESTAMPTZ NOT NULL DEFAULT now(),
    fcmodificacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    TEXT DEFAULT current_user,
    usuario_modificacion TEXT DEFAULT current_user,
    row_version INT NOT NULL DEFAULT 1
);

COMMENT ON TABLE ades_areas_academicas IS 'Áreas académicas globales supervisadas por COORDINADOR_AREA (transversal a planteles)';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Relación coordinador ↔ área académica
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ades_coordinaciones_area (
    id              UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    usuario_id      UUID        NOT NULL REFERENCES ades_usuarios(id),
    area_id         UUID        NOT NULL REFERENCES ades_areas_academicas(id),
    fecha_inicio    DATE        NOT NULL DEFAULT CURRENT_DATE,
    fecha_fin       DATE,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    notas           TEXT,
    fccreacion          TIMESTAMPTZ NOT NULL DEFAULT now(),
    fcmodificacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_creacion    TEXT DEFAULT current_user,
    usuario_modificacion TEXT DEFAULT current_user,
    row_version INT NOT NULL DEFAULT 1,
    UNIQUE (usuario_id, area_id, fecha_inicio)
);

COMMENT ON TABLE ades_coordinaciones_area IS 'Asignación de coordinadores de área a áreas académicas globales';

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Seeds — áreas académicas globales Instituto Nevadi
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ades_areas_academicas (nombre, descripcion, color) VALUES
    ('Matemáticas',         'Aritmética, Álgebra, Geometría, Cálculo',                     '#EF4444'),
    ('Español',             'Lengua y Literatura, Comprensión lectora, Redacción',          '#3B82F6'),
    ('Inglés',              'Lengua Extranjera — todos los niveles',                        '#10B981'),
    ('Ciencias',            'Biología, Química, Física, Ciencias Naturales',               '#8B5CF6'),
    ('Historia y Geografía','Historia Universal, Historia de México, Geografía',            '#F59E0B'),
    ('Formación Cívica',    'Formación Cívica y Ética, Orientación Vocacional',            '#06B6D4'),
    ('Educación Física',    'Activación física, deporte escolar',                           '#22C55E'),
    ('Tecnología',          'Computación, Taller de tecnología, TIC',                       '#6366F1')
ON CONFLICT (nombre) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Eliminar restricción UNIQUE de 1 prof. inglés por plantel (si existe)
-- ─────────────────────────────────────────────────────────────────────────────
-- No existía constraint DB — la limitación era solo en seeds/documentación.
-- Seeds del ciclo 2026-2027 pueden asignar múltiples docentes de inglés por plantel.

-- Comentario de verificación
DO $$
BEGIN
    RAISE NOTICE 'Migración 008 completada: 4 nuevos roles, % áreas académicas',
        (SELECT COUNT(*) FROM ades_areas_academicas);
END $$;
