-- =============================================================================
-- ADES — Seed 004: Plan de Estudios
-- Vincula materias con grados por ciclo escolar
-- Al insertar aquí se activa automáticamente la generación de tareas (Celery)
-- =============================================================================
BEGIN;

-- =============================================================================
-- A. MATERIAS_PLAN: materia ↔ grado ↔ ciclo
-- Primaria: todas las materias aplican a todos los grados 1-6
-- Secundaria: todas las materias aplican a grados 1-3
--   (inglés secundaria tiene mismo prof especial en asignaciones, pero aplica igual)
-- Preparatoria: materias del 1er semestre → solo grado 1 (1er semestre) Metepec
-- =============================================================================

-- PRIMARIA — todas las materias en todos los grados de los 3 planteles
INSERT INTO ades_materias_plan
  (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT
  mat.id,
  gr.id,
  ce.id,
  mat.horas_semana,
  TRUE
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = ne.id
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = ne.id AND ce.es_vigente = TRUE
WHERE ne.nombre_nivel = 'PRIMARIA'
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;

-- SECUNDARIA — todas las materias en todos los grados activos
INSERT INTO ades_materias_plan
  (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT
  mat.id,
  gr.id,
  ce.id,
  mat.horas_semana,
  TRUE
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = ne.id
JOIN ades_ciclos_escolares ce ON ce.nivel_educativo_id = ne.id AND ce.es_vigente = TRUE
WHERE ne.nombre_nivel = 'SECUNDARIA'
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;

-- PREPARATORIA — 1er semestre Metepec (ciclo 26B)
INSERT INTO ades_materias_plan
  (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT
  mat.id,
  gr.id,
  ce.id,
  mat.horas_semana,
  TRUE
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
JOIN ades_grados gr ON gr.nivel_educativo_id = ne.id
JOIN ades_planteles pl ON pl.id = gr.plantel_id AND pl.nombre_plantel = 'Metepec'
JOIN ades_ciclos_escolares ce
  ON ce.nivel_educativo_id = ne.id
 AND ce.nombre_ciclo = '26B'
WHERE ne.nombre_nivel = 'PREPARATORIA'
  AND gr.numero_grado = 1
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO NOTHING;

-- =============================================================================
-- B. TEMAS POR MATERIA (muestra representativa — 4 temas por materia)
-- En producción se cargarán los programas completos
-- Estos temas sirven para que la generación automática de tareas funcione
-- desde el primer día
-- =============================================================================

-- Temas genéricos para PRIMARIA
INSERT INTO ades_temas (nombre_tema, descripcion, materia_id, orden, periodo_sugerido)
SELECT
  'Tema ' || t.num || ': ' || mat.nombre_materia || ' - Unidad ' || t.num,
  'Contenido de la unidad ' || t.num || ' de ' || mat.nombre_materia,
  mat.id,
  t.num,
  -- Distribuir en los 3 bimestres
  CASE WHEN t.num <= 4  THEN 1
       WHEN t.num <= 8  THEN 2
       ELSE 3 END
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id,
generate_series(1,12) AS t(num)
WHERE ne.nombre_nivel = 'PRIMARIA'
ON CONFLICT DO NOTHING;

-- Temas para SECUNDARIA
INSERT INTO ades_temas (nombre_tema, descripcion, materia_id, orden, periodo_sugerido)
SELECT
  'Tema ' || t.num || ': ' || mat.nombre_materia || ' - Bloque ' || t.num,
  'Contenido del bloque ' || t.num || ' de ' || mat.nombre_materia,
  mat.id,
  t.num,
  -- Distribuir en los 6 bimestres
  CASE WHEN t.num <= 2  THEN 1
       WHEN t.num <= 4  THEN 2
       WHEN t.num <= 6  THEN 3
       WHEN t.num <= 8  THEN 4
       WHEN t.num <= 10 THEN 5
       ELSE 6 END
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id,
generate_series(1,12) AS t(num)
WHERE ne.nombre_nivel = 'SECUNDARIA'
ON CONFLICT DO NOTHING;

-- Temas para PREPARATORIA (por semestre: 2 parciales)
INSERT INTO ades_temas (nombre_tema, descripcion, materia_id, orden, periodo_sugerido)
SELECT
  'Tema ' || t.num || ': ' || mat.nombre_materia || ' - Módulo ' || t.num,
  'Contenido del módulo ' || t.num || ' de ' || mat.nombre_materia,
  mat.id,
  t.num,
  CASE WHEN t.num <= 4 THEN 1 ELSE 2 END
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id,
generate_series(1,8) AS t(num)
WHERE ne.nombre_nivel = 'PREPARATORIA'
ON CONFLICT DO NOTHING;

-- =============================================================================
-- C. RÚBRICAS BASE POR NIVEL
-- Una rúbrica genérica por nivel para usar como punto de partida
-- =============================================================================
INSERT INTO ades_rubricas (nombre_rubrica, descripcion, nivel_educativo_id)
SELECT
  'Rúbrica General ' || ne.nombre_nivel,
  'Rúbrica de evaluación general para el nivel ' || ne.nombre_nivel
    || ' — ciclo 2026-2027. Modificar según criterios específicos de cada materia.',
  ne.id
FROM ades_niveles_educativos ne
ON CONFLICT DO NOTHING;

-- Criterios para cada rúbrica
INSERT INTO ades_rubrica_criterios
  (rubrica_id, nombre_criterio, descripcion, ponderacion, orden)
SELECT
  r.id,
  c.criterio,
  c.desc_criterio,
  c.ponderacion,
  c.orden
FROM ades_rubricas r
JOIN ades_niveles_educativos ne ON ne.id = r.nivel_educativo_id,
(VALUES
  ('Conocimiento y comprensión',
   'Dominio de los contenidos y conceptos de la materia', 30.0, 1),
  ('Aplicación y análisis',
   'Capacidad de aplicar el conocimiento en situaciones nuevas', 25.0, 2),
  ('Comunicación',
   'Claridad y organización en la presentación de ideas', 20.0, 3),
  ('Actitud y participación',
   'Participación activa, trabajo en equipo y disposición al aprendizaje', 15.0, 4),
  ('Entrega y puntualidad',
   'Cumplimiento en tiempo y forma de los trabajos asignados', 10.0, 5)
) AS c(criterio, desc_criterio, ponderacion, orden)
ON CONFLICT DO NOTHING;

COMMIT;

DO $$
DECLARE v_plan INT; v_temas INT; v_rubricas INT;
BEGIN
  SELECT COUNT(*) INTO v_plan    FROM ades_materias_plan;
  SELECT COUNT(*) INTO v_temas   FROM ades_temas;
  SELECT COUNT(*) INTO v_rubricas FROM ades_rubricas;
  RAISE NOTICE '=== SEED 004 ===';
  RAISE NOTICE 'Materias en plan: %', v_plan;
  RAISE NOTICE 'Temas generados:  %', v_temas;
  RAISE NOTICE 'Rúbricas base:    %', v_rubricas;
END $$;
