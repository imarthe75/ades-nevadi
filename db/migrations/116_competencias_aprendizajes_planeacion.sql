-- =============================================================================
-- MIGRACION 116: Competencias, Aprendizajes Esperados y Extensión Planeación
-- =============================================================================
-- Objetivo: Agregar infraestructura para planeación semanal con competencias
--           y aprendizajes esperados (SEP NEM + UAEMEX CBU)
-- Fase: 1 (Foundation - Planeaciones Semanales)
-- Fecha: 2026-07-08
-- =============================================================================

BEGIN;

-- =============================================================================
-- 1. CREAR TABLA: ades_competencias
-- =============================================================================
-- Almacena competencias SEP y UAEMEX que orientan la planeación.
-- Alineadas con Campos Formativos de NEM y áreas de conocimiento de CBU.

CREATE TABLE IF NOT EXISTS ades_competencias (
    ref UUID PRIMARY KEY DEFAULT uuidv7(),
    codigo VARCHAR(30) UNIQUE NOT NULL,           -- ES.1.1, MAT.SEC.2, etc.
    nombre VARCHAR(255) NOT NULL,                 -- "Comunicarse en español"
    descripcion TEXT,                             -- Descripción larga
    nivel_educativo_id UUID NOT NULL REFERENCES ades_niveles_educativos(ref),
    campo_formativo VARCHAR(100),                 -- NEM: Lenguajes, SPC, DHC, ENS, Proyecto
    area_conocimiento VARCHAR(100),               -- CBU: Matemáticas, Humanidades, etc.
    orden INT,                                    -- Secuencia dentro del nivel
    activo BOOLEAN DEFAULT TRUE,
    row_version INTEGER DEFAULT 1,
    fecha_creacion TIMESTAMPTZ DEFAULT NOW(),
    fecha_modificacion TIMESTAMPTZ DEFAULT NOW(),
    usuario_creacion VARCHAR(255) DEFAULT current_user,
    usuario_modificacion VARCHAR(255) DEFAULT current_user
);

COMMENT ON TABLE ades_competencias IS
'Competencias y estándares SEP (NEM) y UAEMEX (CBU) que orientan la planeación didáctica docente.
Alineadas con Campos Formativos de Educación Integral (primaria/secundaria) y Áreas de Conocimiento CBU (preparatoria).';

CREATE INDEX idx_competencias_nivel ON ades_competencias(nivel_educativo_id);
CREATE INDEX idx_competencias_campo ON ades_competencias(campo_formativo);
CREATE INDEX idx_competencias_area ON ades_competencias(area_conocimiento);

SELECT auditoria.asignar_biu('public.ades_competencias');

-- =============================================================================
-- 2. CREAR TABLA: ades_aprendizajes_esperados
-- =============================================================================
-- Aprendizajes esperados específicos por grado/materia, alineados con competencias.
-- Referencia directa a SEP 2022 / UAEMEX RGEMS.

CREATE TABLE IF NOT EXISTS ades_aprendizajes_esperados (
    ref UUID PRIMARY KEY DEFAULT uuidv7(),
    codigo VARCHAR(30) UNIQUE NOT NULL,           -- ES.1.1.1, MAT.2.1.3, etc.
    grado_id UUID NOT NULL REFERENCES ades_grados(ref),
    materia_id UUID NOT NULL REFERENCES ades_materias(ref),
    competencia_id UUID REFERENCES ades_competencias(ref),
    descripcion TEXT NOT NULL,                    -- "Lee palabras simples"
    orden INT,                                    -- Secuencia dentro de grado/materia
    activo BOOLEAN DEFAULT TRUE,
    row_version INTEGER DEFAULT 1,
    fecha_creacion TIMESTAMPTZ DEFAULT NOW(),
    fecha_modificacion TIMESTAMPTZ DEFAULT NOW(),
    usuario_creacion VARCHAR(255) DEFAULT current_user,
    usuario_modificacion VARCHAR(255) DEFAULT current_user
);

COMMENT ON TABLE ades_aprendizajes_esperados IS
'Aprendizajes esperados específicos por grado y materia, según SEP NEM (primaria/secundaria)
o UAEMEX RGEMS (preparatoria). Cada uno vinculado a una competencia clave.';

CREATE INDEX idx_aprendizajes_grado ON ades_aprendizajes_esperados(grado_id);
CREATE INDEX idx_aprendizajes_materia ON ades_aprendizajes_esperados(materia_id);
CREATE INDEX idx_aprendizajes_competencia ON ades_aprendizajes_esperados(competencia_id);
CREATE INDEX idx_aprendizajes_grado_materia ON ades_aprendizajes_esperados(grado_id, materia_id);

SELECT auditoria.asignar_biu('public.ades_aprendizajes_esperados');

-- =============================================================================
-- 3. TABLA INTERMEDIA: ades_planeacion_aprendizajes
-- =============================================================================
-- Relaciona cada planeación de clase con los aprendizajes esperados que cubre.
-- Permite tracking granular de cuales aprendizajes se impartieron.

CREATE TABLE IF NOT EXISTS ades_planeacion_aprendizajes (
    ref UUID PRIMARY KEY DEFAULT uuidv7(),
    planeacion_clase_id UUID NOT NULL REFERENCES ades_planeacion_clases(ref),
    aprendizaje_esperado_id UUID NOT NULL REFERENCES ades_aprendizajes_esperados(ref),
    indicador_logro VARCHAR(255),                 -- Cómo medir que se logró
    es_completado BOOLEAN DEFAULT FALSE,
    fecha_completado TIMESTAMPTZ,
    observaciones TEXT,                           -- Notas del profesor
    row_version INTEGER DEFAULT 1,
    fecha_creacion TIMESTAMPTZ DEFAULT NOW(),
    fecha_modificacion TIMESTAMPTZ DEFAULT NOW(),
    usuario_creacion VARCHAR(255) DEFAULT current_user,
    usuario_modificacion VARCHAR(255) DEFAULT current_user
);

COMMENT ON TABLE ades_planeacion_aprendizajes IS
'Relación entre cada planeación de clase y los aprendizajes esperados que se pretendía lograr.
Permite auditar cuál fue el avance real en el logro de competencias.';

CREATE INDEX idx_planeacion_aprendizajes_planeacion ON ades_planeacion_aprendizajes(planeacion_clase_id);
CREATE INDEX idx_planeacion_aprendizajes_aprendizaje ON ades_planeacion_aprendizajes(aprendizaje_esperado_id);

SELECT auditoria.asignar_biu('public.ades_planeacion_aprendizajes');

-- =============================================================================
-- 4. ALTER TABLE: ades_planeacion_clases
-- =============================================================================
-- Extender con campos necesarios para planeación semanal estructurada.

ALTER TABLE ades_planeacion_clases
    ADD COLUMN IF NOT EXISTS numero_trimestre SMALLINT,           -- 1, 2, 3
    ADD COLUMN IF NOT EXISTS numero_semana SMALLINT,              -- 1-40
    ADD COLUMN IF NOT EXISTS modalidad VARCHAR(20),               -- PRESENCIAL, VIRTUAL, HIBRIDA
    ADD COLUMN IF NOT EXISTS competencia_id UUID REFERENCES ades_competencias(ref),
    ADD COLUMN IF NOT EXISTS fecha_fin DATE;                      -- Fecha final de la semana

-- Comentarios en nuevas columnas
COMMENT ON COLUMN ades_planeacion_clases.numero_trimestre IS 'Trimestre académico: 1 (ago-oct), 2 (nov-ene), 3 (feb-abr)';
COMMENT ON COLUMN ades_planeacion_clases.numero_semana IS 'Número de semana del ciclo escolar (1-40)';
COMMENT ON COLUMN ades_planeacion_clases.modalidad IS 'Modalidad de impartición: PRESENCIAL, VIRTUAL, HIBRIDA';
COMMENT ON COLUMN ades_planeacion_clases.competencia_id IS 'Competencia principal que se trabaja en esta planeación';
COMMENT ON COLUMN ades_planeacion_clases.fecha_fin IS 'Fecha final de ejecución de la planeación (si es diferente a fecha_planeada)';

-- Crear índices para nuevos campos
CREATE INDEX IF NOT EXISTS idx_planeacion_clases_trimestre ON ades_planeacion_clases(numero_trimestre);
CREATE INDEX IF NOT EXISTS idx_planeacion_clases_semana ON ades_planeacion_clases(numero_semana);
CREATE INDEX IF NOT EXISTS idx_planeacion_clases_modalidad ON ades_planeacion_clases(modalidad);
CREATE INDEX IF NOT EXISTS idx_planeacion_clases_competencia ON ades_planeacion_clases(competencia_id);
CREATE INDEX IF NOT EXISTS idx_planeacion_clases_grupo_trimestre ON ades_planeacion_clases(grupo_id, numero_trimestre, numero_semana);

-- =============================================================================
-- 5. VIEW: vw_planeacion_cobertura_semanal
-- =============================================================================
-- Vista para consultar cobertura semanal: temas planeados vs impartidos.

CREATE OR REPLACE VIEW vw_planeacion_cobertura_semanal AS
SELECT
    g.ref as grupo_id,
    g.nombre_grupo,
    pc.numero_trimestre as trimestre,
    pc.numero_semana as semana,
    m.ref as materia_id,
    m.nombre_materia,
    COUNT(DISTINCT pc.ref) as temas_planeados,
    COUNT(DISTINCT CASE WHEN ap.es_completado THEN ap.ref END) as temas_impartidos,
    ROUND(
        COUNT(DISTINCT CASE WHEN ap.es_completado THEN ap.ref END)::NUMERIC /
        NULLIF(COUNT(DISTINCT pc.ref), 0) * 100, 1
    ) as pct_cobertura
FROM ades_planeacion_clases pc
JOIN ades_grupos g ON pc.grupo_id = g.ref
JOIN ades_temas t ON pc.tema_id = t.ref
JOIN ades_materias m ON t.materia_id = m.ref
LEFT JOIN ades_avance_planificacion ap ON pc.ref = ap.planeacion_clase_id
WHERE pc.is_active AND g.is_active AND m.is_active
GROUP BY g.ref, g.nombre_grupo, pc.numero_trimestre, pc.numero_semana, m.ref, m.nombre_materia;

-- =============================================================================
-- 6. VERIFICACIÓN Y CLEANUP
-- =============================================================================

-- Verificar que las tablas tienen estructura correcta
SELECT
    'ades_competencias'::text as tabla,
    COUNT(*)::int as columnas
FROM information_schema.columns
WHERE table_name = 'ades_competencias'
UNION ALL
SELECT
    'ades_aprendizajes_esperados'::text,
    COUNT(*)::int
FROM information_schema.columns
WHERE table_name = 'ades_aprendizajes_esperados'
UNION ALL
SELECT
    'ades_planeacion_aprendizajes'::text,
    COUNT(*)::int
FROM information_schema.columns
WHERE table_name = 'ades_planeacion_aprendizajes';

COMMIT;

-- =============================================================================
-- NOTAS DE EJECUCIÓN
-- =============================================================================
-- 1. Ejecutar: psql -U ades_admin -d ades < 116_competencias_aprendizajes_planeacion.sql
-- 2. Verificar auditoría: SELECT * FROM information_schema.triggers WHERE event_object_table = 'ades_competencias';
-- 3. Próxima: seed data con competencias y aprendizajes (archivo 117_*)
-- 4. Testing: Playwright suite para cascada Nivel → Competencia → Aprendizaje
-- =============================================================================
