-- =============================================================================
-- ADES — Seed 004b: Materias REALES de PRIMARIA (Instituto Nevadi)
--
-- Integra el plan de estudios real de la primaria Nevadi (el que se usó para
-- generar el horario) al modelo del proyecto. Sustituye las materias genéricas
-- NEM por las materias operativas reales, con HORAS POR GRADO.
--
-- Idempotente. Ejecutar DESPUÉS de 001_datos_base.sql. Reemplaza el bloque de
-- primaria de 004_plan_estudios.sql (ver nota al final sobre limpieza).
--
-- Requisitos del modelo (ya existen):
--   ades_materias.tipo_materia        -> 'TITULAR' | 'ESPECIALISTA'
--   ades_materias_plan.horas_semana   -> horas POR GRADO (clave de la variación)
-- =============================================================================
BEGIN;

-- -----------------------------------------------------------------------------
-- 1. MATERIAS (catálogo nivel PRIMARIA)
--    es_inglés=TRUE solo Inglés.  tipo_materia distingue quién la imparte.
--    horas_semana aquí es solo un valor de referencia; el real va por grado (paso 2).
--    Las materias que ya existían por nombre (Inglés, Educación Física, Artes) se
--    actualizan (upsert); las nuevas se insertan.
-- -----------------------------------------------------------------------------
INSERT INTO ades_materias
  (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, es_inglés, tipo_materia)
SELECT m.nombre, m.clave, ne.id, m.horas_ref, m.es_ing, m.tipo
FROM ades_niveles_educativos ne,
(VALUES
  -- Impartidas por el DOCENTE TITULAR
  ('Lecto',                     'PRI-LECTO', 7.0, FALSE, 'TITULAR'),
  ('Español',                   'PRI-ESP',   5.0, FALSE, 'TITULAR'),
  ('Matemáticas',               'PRI-MAT',   7.0, FALSE, 'TITULAR'),
  ('Conocimiento del Medio',    'PRI-CONO',  2.0, FALSE, 'TITULAR'),
  ('Artes',                     'PRI-ART',   1.0, FALSE, 'TITULAR'),
  ('Fábrica de Lectura',        'PRI-FAB',   1.0, FALSE, 'TITULAR'),
  ('Ortografía',                'PRI-ORT',   1.0, FALSE, 'TITULAR'),
  ('Formación Cívica y Ética',  'PRI-FCE',   1.0, FALSE, 'TITULAR'),
  ('La Entidad donde Vivo',     'PRI-ENT',   1.0, FALSE, 'TITULAR'),
  ('Geografía',                 'PRI-GEO',   1.0, FALSE, 'TITULAR'),
  ('Historia',                  'PRI-HIS',   1.0, FALSE, 'TITULAR'),
  ('Proyectos',                 'PRI-PROY',  3.0, FALSE, 'TITULAR'),
  -- Impartidas por DOCENTE ESPECIALISTA (no el titular)
  ('Inglés',                    'PRI-ING',   4.0, TRUE,  'ESPECIALISTA'),
  ('Socioemocional',            'PRI-SOC',   1.0, FALSE, 'ESPECIALISTA'),
  ('Educación Física',          'PRI-EDF',   2.0, FALSE, 'ESPECIALISTA'),
  ('Desarrollo Comunitario',    'PRI-DCOM',  1.0, FALSE, 'ESPECIALISTA'),
  ('Computación',               'PRI-COMP',  1.0, FALSE, 'ESPECIALISTA')
) AS m(nombre, clave, horas_ref, es_ing, tipo)
WHERE ne.nombre_nivel = 'PRIMARIA'
ON CONFLICT (nombre_materia, nivel_educativo_id) DO UPDATE
  SET clave_materia = EXCLUDED.clave_materia,
      tipo_materia  = EXCLUDED.tipo_materia,
      es_inglés     = EXCLUDED.es_inglés;

-- -----------------------------------------------------------------------------
-- 2. PLAN DE ESTUDIOS — HORAS POR GRADO (1°..6°) para el ciclo PRIMARIA vigente
--    El arreglo es [h1°, h2°, h3°, h4°, h5°, h6°]; 0 = no se imparte ese grado.
--    Aplica a TODOS los grados de primaria del/los plantel(es) (vía numero_grado).
-- -----------------------------------------------------------------------------
INSERT INTO ades_materias_plan
  (materia_id, grado_id, ciclo_escolar_id, horas_semana, es_obligatoria)
SELECT mat.id, gr.id, ce.id, h.horas, TRUE
FROM (
  VALUES
    ('PRI-LECTO', ARRAY[7,5,0,0,0,0]),
    ('PRI-ESP',   ARRAY[1,3,6,5,5,5]),
    ('PRI-MAT',   ARRAY[7,7,7,6,6,6]),
    ('PRI-CONO',  ARRAY[1,1,2,2,2,2]),
    ('PRI-ART',   ARRAY[1,1,1,1,1,1]),
    ('PRI-FAB',   ARRAY[1,1,1,1,1,1]),
    ('PRI-ORT',   ARRAY[1,1,1,1,1,1]),
    ('PRI-FCE',   ARRAY[1,1,1,1,1,1]),
    ('PRI-ENT',   ARRAY[0,0,1,0,0,0]),
    ('PRI-GEO',   ARRAY[0,0,0,1,1,1]),
    ('PRI-HIS',   ARRAY[0,0,0,1,1,1]),
    ('PRI-PROY',  ARRAY[2,2,2,3,3,3]),
    ('PRI-ING',   ARRAY[4,4,4,4,4,4]),
    ('PRI-SOC',   ARRAY[1,1,1,1,1,1]),
    ('PRI-EDF',   ARRAY[2,2,2,2,2,2]),
    ('PRI-DCOM',  ARRAY[1,1,1,1,1,1]),
    ('PRI-COMP',  ARRAY[1,1,1,1,1,1])
) AS plan(clave, horas_por_grado)
JOIN ades_materias mat            ON mat.clave_materia = plan.clave
JOIN ades_niveles_educativos ne   ON ne.id = mat.nivel_educativo_id AND ne.nombre_nivel = 'PRIMARIA'
JOIN ades_grados gr               ON gr.nivel_educativo_id = ne.id AND gr.numero_grado BETWEEN 1 AND 6
JOIN ades_ciclos_escolares ce     ON ce.nivel_educativo_id = ne.id AND ce.es_vigente = TRUE
CROSS JOIN LATERAL (SELECT (plan.horas_por_grado)[gr.numero_grado] AS horas) h
WHERE h.horas > 0
ON CONFLICT (materia_id, grado_id, ciclo_escolar_id) DO UPDATE
  SET horas_semana = EXCLUDED.horas_semana,
      es_obligatoria = TRUE;

-- -----------------------------------------------------------------------------
-- 3. (OPCIONAL — DECISIÓN) Retirar del PLAN las materias genéricas NEM de primaria
--    que NO usa Nevadi (Lenguajes, Saberes y Pensamiento Científico,
--    Ética/Naturaleza/Sociedades, De lo Humano y lo Comunitario).
--    Se eliminan solo sus filas de PLAN (no la materia) para no romper otras FKs.
--    Descomenta si quieres que el plan quede SOLO con las materias reales Nevadi.
-- -----------------------------------------------------------------------------
-- DELETE FROM ades_materias_plan mp
-- USING ades_materias mat, ades_niveles_educativos ne
-- WHERE mp.materia_id = mat.id
--   AND mat.nivel_educativo_id = ne.id
--   AND ne.nombre_nivel = 'PRIMARIA'
--   AND mat.clave_materia IN ('PRI-LEN','PRI-SPC','PRI-ENS','PRI-DHC');

COMMIT;

-- =============================================================================
-- VERIFICACIÓN
-- =============================================================================
DO $$
DECLARE v_mat INT; v_plan INT; v_g1 NUMERIC; v_g4 NUMERIC;
BEGIN
  SELECT COUNT(*) INTO v_mat FROM ades_materias mat
    JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
   WHERE ne.nombre_nivel='PRIMARIA' AND mat.clave_materia LIKE 'PRI-%';
  SELECT COUNT(*) INTO v_plan FROM ades_materias_plan mp
    JOIN ades_materias mat ON mat.id = mp.materia_id
    JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
   WHERE ne.nombre_nivel='PRIMARIA';
  -- Total de horas/semana en 1° y 4° (deben dar 31 con materias Nevadi)
  SELECT COALESCE(SUM(mp.horas_semana),0) INTO v_g1 FROM ades_materias_plan mp
    JOIN ades_grados gr ON gr.id=mp.grado_id JOIN ades_niveles_educativos ne ON ne.id=gr.nivel_educativo_id
    JOIN ades_materias mat ON mat.id=mp.materia_id
   WHERE ne.nombre_nivel='PRIMARIA' AND gr.numero_grado=1 AND mat.clave_materia LIKE 'PRI-%';
  SELECT COALESCE(SUM(mp.horas_semana),0) INTO v_g4 FROM ades_materias_plan mp
    JOIN ades_grados gr ON gr.id=mp.grado_id JOIN ades_niveles_educativos ne ON ne.id=gr.nivel_educativo_id
    JOIN ades_materias mat ON mat.id=mp.materia_id
   WHERE ne.nombre_nivel='PRIMARIA' AND gr.numero_grado=4 AND mat.clave_materia LIKE 'PRI-%';
  RAISE NOTICE '=== SEED 004b (materias primaria Nevadi) ===';
  RAISE NOTICE 'Materias PRI-*:            %', v_mat;
  RAISE NOTICE 'Filas de plan primaria:    %', v_plan;
  RAISE NOTICE 'Horas/sem 1° (esperado 31): %', v_g1;
  RAISE NOTICE 'Horas/sem 4° (esperado 31): %', v_g4;
END $$;

-- =============================================================================
-- PENDIENTES DE INTEGRACIÓN (requieren decisión / otro seed):
--
-- A) ASIGNACIÓN DOCENTE: el seed 002 (D1) asigna al TITULAR todas las materias
--    excepto inglés. Con Nevadi hay MÁS especialistas. Debe cambiarse para que
--    las materias con tipo_materia='ESPECIALISTA' (Socioemocional, Educación
--    Física, Desarrollo Comunitario, Computación, Inglés) se asignen a docentes
--    especialistas y NO al titular. Esto requiere además sembrar esos docentes
--    especialistas por plantel (hoy solo existe el de inglés).
--      - Inglés y Socioemocional: 1 docente para primaria baja (1°-3°) y otro
--        para alta (4°-6°).
--      - Educación Física, Desarrollo Comunitario, Computación: 1 docente c/u.
--
-- B) Si activaste el paso 3 (DELETE), revisa que no haya ades_asignaciones_docentes
--    ni ades_temas que dependan de las materias NEM retiradas del plan.
-- =============================================================================
