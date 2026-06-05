-- =============================================================================
-- Migración 015 — Planes de estudio vigentes 2025-2026
--
--   1. PREPARATORIA: CBU 2024 UAEMEX (reemplaza plan anterior)
--      Fuente: DENMS UAEMEX / Milenio 2024-02-29
--      6 semestres, 53 asignaturas (49 obligatorias + 4 optativas elegibles)
--
--   2. SECUNDARIA: NEM 2022 (4 campos formativos — reemplaza plan 2006/2011)
--      Fuente: SEP Plan de Estudios 2022 - Fases 4-6
--      3 grados, totalmente homologado con primaria NEM
--
--   3. ades_identidad_institucional: ampliar con campos de branding
-- =============================================================================

-- ── 0. Preparar: desactivar planes anteriores ─────────────────────────────────
-- Desactivar asignaciones de preparatoria (old plan)
UPDATE ades_materias_plan mp
   SET is_active = FALSE
  FROM ades_materias m
  JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
 WHERE mp.materia_id = m.id AND n.nombre_nivel = 'PREPARATORIA';

-- Desactivar materias de preparatoria (old plan)
UPDATE ades_materias
   SET is_active = FALSE
 WHERE nivel_educativo_id = (
   SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'PREPARATORIA' LIMIT 1
 );

-- Desactivar asignaciones de secundaria (old plan 2006/2011)
UPDATE ades_materias_plan mp
   SET is_active = FALSE
  FROM ades_materias m
  JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
 WHERE mp.materia_id = m.id AND n.nombre_nivel = 'SECUNDARIA';

-- Desactivar materias de secundaria (old plan)
UPDATE ades_materias
   SET is_active = FALSE
 WHERE nivel_educativo_id = (
   SELECT id FROM ades_niveles_educativos WHERE nombre_nivel = 'SECUNDARIA' LIMIT 1
 );

-- ══════════════════════════════════════════════════════════════════════════════
-- 1. CBU 2024 UAEMEX — Asignaturas obligatorias por semestre
-- ══════════════════════════════════════════════════════════════════════════════
-- Horas semanales estimadas con base en la carga típica de 36 h/sem del CBU

DO $$
DECLARE
  v_nivel_id UUID;
BEGIN
  SELECT id INTO v_nivel_id FROM ades_niveles_educativos WHERE nombre_nivel = 'PREPARATORIA' LIMIT 1;

  -- ── Semestre 1 ─────────────────────────────────────────────────────────────
  INSERT INTO ades_materias (nombre_materia, clave_materia, nivel_educativo_id, horas_semana)
  VALUES
    ('Lengua y Comunicación 1',             'CBU-LC1',  v_nivel_id, 5),
    ('Inglés 1',                            'CBU-ING1', v_nivel_id, 3),
    ('Cultura Digital',                     'CBU-CD',   v_nivel_id, 3),
    ('Pensamiento Matemático 1',            'CBU-PM1',  v_nivel_id, 5),
    ('Química 1',                           'CBU-QUI1', v_nivel_id, 4),
    ('Humanidades 1',                       'CBU-HUM1', v_nivel_id, 4),
    ('Ciencias Sociales 1',                 'CBU-CS1',  v_nivel_id, 4),
    ('Recursos y Ámbitos Socioemocionales 1','CBU-RAS1', v_nivel_id, 3),
    ('Actividades Físicas y Deportivas 1',  'CBU-AFD1', v_nivel_id, 2),
  -- ── Semestre 2 ─────────────────────────────────────────────────────────────
    ('Lengua y Comunicación 2',             'CBU-LC2',  v_nivel_id, 5),
    ('Inglés 2',                            'CBU-ING2', v_nivel_id, 3),
    ('Pensamiento Matemático 2',            'CBU-PM2',  v_nivel_id, 5),
    ('Física 1',                            'CBU-FIS1', v_nivel_id, 4),
    ('Humanidades 2',                       'CBU-HUM2', v_nivel_id, 4),
    ('Ciencias Sociales 2',                 'CBU-CS2',  v_nivel_id, 4),
    ('Desarrollo Personal y Emocional',     'CBU-DPE',  v_nivel_id, 3),
    ('Recursos y Ámbitos Socioemocionales 2','CBU-RAS2', v_nivel_id, 3),
    ('Actividades Físicas y Deportivas 2',  'CBU-AFD2', v_nivel_id, 2),
  -- ── Semestre 3 ─────────────────────────────────────────────────────────────
    ('Lengua y Comunicación 3',             'CBU-LC3',  v_nivel_id, 5),
    ('Inglés 3',                            'CBU-ING3', v_nivel_id, 3),
    ('Pensamiento Matemático 3',            'CBU-PM3',  v_nivel_id, 5),
    ('Biología 1',                          'CBU-BIO1', v_nivel_id, 4),
    ('Humanidades 3',                       'CBU-HUM3', v_nivel_id, 4),
    ('Metodología de Investigación y Taller 1','CBU-MIT1',v_nivel_id,3),
    ('Desarrollo Social',                   'CBU-DS',   v_nivel_id, 3),
    ('Recursos y Ámbitos Socioemocionales 3','CBU-RAS3', v_nivel_id, 3),
    ('Actividades Físicas y Deportivas 3',  'CBU-AFD3', v_nivel_id, 2),
  -- ── Semestre 4 ─────────────────────────────────────────────────────────────
    ('Literatura',                          'CBU-LIT',  v_nivel_id, 4),
    ('Inglés 4',                            'CBU-ING4', v_nivel_id, 3),
    ('Temas Selectos de Matemáticas 1',     'CBU-TSM1', v_nivel_id, 4),
    ('Conciencia Histórica 1',              'CBU-CH1',  v_nivel_id, 4),
    ('Química 2',                           'CBU-QUI2', v_nivel_id, 4),
    ('Cultura Ambiental y Desarrollo Sustentable','CBU-CADS',v_nivel_id,3),
    ('Metodología de Investigación y Taller 2','CBU-MIT2',v_nivel_id,3),
    ('Orientación Vocacional',              'CBU-OV',   v_nivel_id, 2),
    ('Actividades Físicas y Deportivas 4',  'CBU-AFD4', v_nivel_id, 2),
  -- ── Semestre 5 ─────────────────────────────────────────────────────────────
    ('Temas Selectos de Matemáticas 2',     'CBU-TSM2', v_nivel_id, 4),
    ('Conciencia Histórica 2',              'CBU-CH2',  v_nivel_id, 4),
    ('Física 2',                            'CBU-FIS2', v_nivel_id, 4),
    ('Geografía',                           'CBU-GEO',  v_nivel_id, 3),
    ('Apreciación y Expresión Artística 1', 'CBU-AEA1', v_nivel_id, 3),
    ('Cultura de Paz',                      'CBU-CP',   v_nivel_id, 3),
  -- ── Semestre 6 ─────────────────────────────────────────────────────────────
    ('Temas Selectos de Matemáticas 3',     'CBU-TSM3', v_nivel_id, 4),
    ('Conciencia Histórica 3',              'CBU-CH3',  v_nivel_id, 4),
    ('Biología 2',                          'CBU-BIO2', v_nivel_id, 4),
    ('Apreciación y Expresión Artística 2', 'CBU-AEA2', v_nivel_id, 3),
    ('Desarrollo Emprendedor',              'CBU-DE',   v_nivel_id, 3),
    ('Psicología',                          'CBU-PSI',  v_nivel_id, 3),
  -- ── Optativas (elegibles — disponibles en sem 5 y 6) ──────────────────────
    ('Optativa 1',                          'CBU-OPT1', v_nivel_id, 4),
    ('Optativa 2',                          'CBU-OPT2', v_nivel_id, 4),
    ('Optativa 3',                          'CBU-OPT3', v_nivel_id, 4),
    ('Optativa 4',                          'CBU-OPT4', v_nivel_id, 4)
  ON CONFLICT DO NOTHING;
END $$;

-- Asignar materias CBU 2024 a los grados (semestres) de cada plantel
-- Semestre 1
INSERT INTO ades_materias_plan (materia_id, grado_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, m.horas_semana, (m.clave_materia NOT LIKE 'CBU-OPT%')
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 1
WHERE n.nombre_nivel = 'PREPARATORIA'
  AND m.clave_materia IN ('CBU-LC1','CBU-ING1','CBU-CD','CBU-PM1','CBU-QUI1',
                          'CBU-HUM1','CBU-CS1','CBU-RAS1','CBU-AFD1')
  AND m.is_active = TRUE
ON CONFLICT DO NOTHING;

-- Semestre 2
INSERT INTO ades_materias_plan (materia_id, grado_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 2
WHERE n.nombre_nivel = 'PREPARATORIA'
  AND m.clave_materia IN ('CBU-LC2','CBU-ING2','CBU-PM2','CBU-FIS1','CBU-HUM2',
                          'CBU-CS2','CBU-DPE','CBU-RAS2','CBU-AFD2')
  AND m.is_active = TRUE
ON CONFLICT DO NOTHING;

-- Semestre 3
INSERT INTO ades_materias_plan (materia_id, grado_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 3
WHERE n.nombre_nivel = 'PREPARATORIA'
  AND m.clave_materia IN ('CBU-LC3','CBU-ING3','CBU-PM3','CBU-BIO1','CBU-HUM3',
                          'CBU-MIT1','CBU-DS','CBU-RAS3','CBU-AFD3')
  AND m.is_active = TRUE
ON CONFLICT DO NOTHING;

-- Semestre 4
INSERT INTO ades_materias_plan (materia_id, grado_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 4
WHERE n.nombre_nivel = 'PREPARATORIA'
  AND m.clave_materia IN ('CBU-LIT','CBU-ING4','CBU-TSM1','CBU-CH1','CBU-QUI2',
                          'CBU-CADS','CBU-MIT2','CBU-OV','CBU-AFD4')
  AND m.is_active = TRUE
ON CONFLICT DO NOTHING;

-- Semestre 5 (obligatorias + 2 optativas elegibles)
INSERT INTO ades_materias_plan (materia_id, grado_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, m.horas_semana,
       (m.clave_materia NOT LIKE 'CBU-OPT%')
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 5
WHERE n.nombre_nivel = 'PREPARATORIA'
  AND m.clave_materia IN ('CBU-TSM2','CBU-CH2','CBU-FIS2','CBU-GEO',
                          'CBU-AEA1','CBU-CP','CBU-OPT1','CBU-OPT2')
  AND m.is_active = TRUE
ON CONFLICT DO NOTHING;

-- Semestre 6 (obligatorias + 2 optativas elegibles)
INSERT INTO ades_materias_plan (materia_id, grado_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, m.horas_semana,
       (m.clave_materia NOT LIKE 'CBU-OPT%')
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id AND gr.numero_grado = 6
WHERE n.nombre_nivel = 'PREPARATORIA'
  AND m.clave_materia IN ('CBU-TSM3','CBU-CH3','CBU-BIO2','CBU-AEA2',
                          'CBU-DE','CBU-PSI','CBU-OPT3','CBU-OPT4')
  AND m.is_active = TRUE
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════════════════════
-- 2. NEM 2022 — Secundaria (4 campos formativos, Fase 4-6)
-- ══════════════════════════════════════════════════════════════════════════════
-- Los 4 campos formativos aplican igual a los 3 grados (como en primaria).
-- Cada campo tiene libros/proyectos que integran múltiples disciplinas.

DO $$
DECLARE
  v_nivel_id UUID;
BEGIN
  SELECT id INTO v_nivel_id FROM ades_niveles_educativos WHERE nombre_nivel = 'SECUNDARIA' LIMIT 1;

  INSERT INTO ades_materias (nombre_materia, clave_materia, nivel_educativo_id, horas_semana)
  VALUES
    ('Lenguajes',                        'SEC-LEN',  v_nivel_id, 10),
    ('Saberes y Pensamiento Científico', 'SEC-SPC',  v_nivel_id,  9),
    ('Ética, Naturaleza y Sociedades',   'SEC-ENS',  v_nivel_id,  7),
    ('De lo Humano y lo Comunitario',    'SEC-DHC',  v_nivel_id,  4),
    ('Proyectos Escolares',              'SEC-PRY',  v_nivel_id,  2)
  ON CONFLICT DO NOTHING;
END $$;

-- Asignar a los 3 grados de secundaria de cada plantel
INSERT INTO ades_materias_plan (materia_id, grado_id, horas_semana, es_obligatoria)
SELECT m.id, gr.id, m.horas_semana, TRUE
FROM ades_materias m
JOIN ades_niveles_educativos n ON n.id = m.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = n.id
WHERE n.nombre_nivel = 'SECUNDARIA'
  AND m.is_active = TRUE
  AND m.clave_materia IN ('SEC-LEN','SEC-SPC','SEC-ENS','SEC-DHC','SEC-PRY')
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════════════════════
-- 3. Ampliar ades_identidad_institucional
-- ══════════════════════════════════════════════════════════════════════════════
ALTER TABLE ades_identidad_institucional
  ADD COLUMN IF NOT EXISTS nombre_clave  VARCHAR(50),   -- e.g.: NOMBRE_INSTITUCION, COLOR_PRIMARIO, FAVICON_URL
  ADD COLUMN IF NOT EXISTS descripcion   VARCHAR(200);

-- Completar datos de Instituto Nevadi
UPDATE ades_identidad_institucional
   SET color_hex = '#D02030'
 WHERE tipo_elemento = 'COLOR_PRIMARIO' AND (color_hex IS NULL OR color_hex = '');

-- Agregar elementos faltantes de identidad
INSERT INTO ades_identidad_institucional (tipo_elemento, nombre_clave, texto_elemento, color_hex, descripcion)
VALUES
  ('NOMBRE_INSTITUCION', 'nombre_institucion', 'Instituto Nevadi',        NULL,      'Nombre oficial de la institución'),
  ('NOMBRE_SISTEMA',     'nombre_sistema',     'ADES',                    NULL,      'Nombre del sistema escolar'),
  ('COLOR_SECUNDARIO',   'color_secundario',   NULL,                     '#1e293b',  'Color secundario (texto, fondos oscuros)'),
  ('COLOR_ACENTO',       'color_acento',       NULL,                     '#f1f5f9',  'Color de acento (fondos claros)'),
  ('FAVICON_URL',        'favicon_url',        'favicon.png',             NULL,      'Ruta al favicon institucional'),
  ('LOGO_URL',           'logo_url',           'nevadi-logo.jpg',         NULL,      'Ruta al logo horizontal'),
  ('FOOTER_TEXTO',       'footer_texto',       '© Instituto Nevadi 2026. Todos los derechos reservados.', NULL, 'Texto del pie de página'),
  ('META_DESCRIPCION',   'meta_descripcion',   'Sistema de Administración Escolar — Instituto Nevadi', NULL, 'Descripción para SEO/meta tags')
ON CONFLICT DO NOTHING;

COMMENT ON TABLE ades_identidad_institucional IS
  'Branding y configuración visual del sistema. Un registro por elemento de identidad.
   Soporte de scope: plantel_id NULL = aplica a toda la institución.
   Tipos: NOMBRE_INSTITUCION, COLOR_PRIMARIO, COLOR_SECUNDARIO, LOGO_URL, FAVICON_URL, SLOGAN, FOOTER_TEXTO.';
