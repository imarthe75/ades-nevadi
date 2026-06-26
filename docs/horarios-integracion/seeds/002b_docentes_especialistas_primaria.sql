-- =============================================================================
-- ADES — Seed 002b: Docentes ESPECIALISTAS de PRIMARIA + asignación corregida
--
-- Complementa 004b_materias_primaria_nevadi.sql. Corrige el modelo de asignación:
--   - El TITULAR imparte solo materias tipo_materia='TITULAR'.
--   - Las materias tipo_materia='ESPECIALISTA' las dan docentes especialistas.
--
-- Diseño de asignación (data-driven, reutilizable):
--   * Un docente CUBRE A y B (todas las secciones de los grados que atiende).
--   * Un docente puede atender VARIOS GRADOS (rango gmin..gmax).
--   * Un docente puede impartir VARIAS MATERIAS (arreglo 'materias').
--
-- Orden de ejecución: 001 -> 002 -> 004 -> 004b -> 002b
-- Idempotente. Requiere que 004b haya fijado tipo_materia en ades_materias.
-- =============================================================================
BEGIN;

-- -----------------------------------------------------------------------------
-- 0. Definición de docentes especialistas (1 fila = 1 docente por plantel)
--    Coincide con el modelo del horario: Inglés y Socioemocional separados por
--    primaria baja (1-3) y alta (4-6); EF, Desarrollo Comunitario y Computación
--    con UN docente para todos los grados (1-6).
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE tmp_esp (clave text, nombre text, materias text[], gmin int, gmax int, genero text) ON COMMIT DROP;
INSERT INTO tmp_esp VALUES
  ('ING-BAJA', 'Inglés Primaria Baja',          ARRAY['Inglés'],                 1, 3, 'F'),
  ('ING-ALTA', 'Inglés Primaria Alta',          ARRAY['Inglés'],                 4, 6, 'M'),
  ('SOC-BAJA', 'Socioemocional Primaria Baja',  ARRAY['Socioemocional'],         1, 3, 'F'),
  ('SOC-ALTA', 'Socioemocional Primaria Alta',  ARRAY['Socioemocional'],         4, 6, 'F'),
  ('EDF',      'Educación Física Primaria',     ARRAY['Educación Física'],       1, 6, 'M'),
  ('DCOM',     'Desarrollo Comunitario Prim.',  ARRAY['Desarrollo Comunitario'], 1, 6, 'M'),
  ('COMP',     'Computación Primaria',          ARRAY['Computación'],            1, 6, 'F');
-- Para que UN docente imparta VARIAS materias, agrega varias al arreglo, p.ej.:
--   ('SOC-DCOM','Socio + Desarrollo Com.', ARRAY['Socioemocional','Desarrollo Comunitario'], 1, 6, 'F')
-- (y elimina las filas individuales que reemplace).

-- Planteles que ofrecen PRIMARIA (los especialistas se crean por plantel)
CREATE TEMP TABLE tmp_pp ON COMMIT DROP AS
SELECT DISTINCT pl.id AS plantel_id, pl.nombre_plantel
FROM ades_planteles pl
JOIN ades_grados gr ON gr.plantel_id = pl.id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id AND ne.nombre_nivel = 'PRIMARIA';

-- -----------------------------------------------------------------------------
-- 1. PERSONAS de los especialistas (1 por plantel x docente)
-- -----------------------------------------------------------------------------
INSERT INTO ades_personas (nombre, apellido_paterno, apellido_materno, curp, genero)
SELECT
  'Docente ' || te.nombre,
  pp.nombre_plantel || ' Prim',
  'NVD',
  'XEXX' || UPPER(LEFT(REGEXP_REPLACE(pp.nombre_plantel,'[^A-Za-z]','','g'),3)) || 'PR'
    || LPAD((ROW_NUMBER() OVER (ORDER BY pp.nombre_plantel, te.clave))::text, 9, '0'),  -- 18 chars
  te.genero
FROM tmp_esp te
CROSS JOIN tmp_pp pp
ON CONFLICT (curp) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. PROFESORES (vincula persona ↔ plantel)
-- -----------------------------------------------------------------------------
INSERT INTO ades_profesores
  (numero_empleado, persona_id, plantel_id, estatus_id, tipo_contrato, especialidad, turno)
SELECT
  'EMP-ESP-PRI-' || UPPER(LEFT(REGEXP_REPLACE(pp.nombre_plantel,'[^A-Za-z]','','g'),3)) || '-' || te.clave,
  per.id, pp.plantel_id, est.id, 'BASE',
  array_to_string(te.materias, ', '),
  'MATUTINO'
FROM tmp_esp te
CROSS JOIN tmp_pp pp
JOIN ades_personas per
  ON per.nombre = 'Docente ' || te.nombre
 AND per.apellido_paterno = pp.nombre_plantel || ' Prim'
 AND per.apellido_materno = 'NVD'
CROSS JOIN ades_estatus est
WHERE est.entidad = 'PROFESOR' AND est.nombre_estatus = 'ACTIVO'
ON CONFLICT (numero_empleado) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. LIMPIAR asignaciones ESPECIALISTA de primaria (corrige titular + inglés viejo)
-- -----------------------------------------------------------------------------
DELETE FROM ades_asignaciones_docentes ad
USING ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
WHERE ad.materia_id = mat.id
  AND ne.nombre_nivel = 'PRIMARIA'
  AND mat.tipo_materia = 'ESPECIALISTA';

-- -----------------------------------------------------------------------------
-- 4. ASIGNAR ESPECIALISTAS: docente -> (sus materias) x (grupos A y B de sus grados)
--    Se respeta el plan (solo donde la materia aplica al grado).
-- -----------------------------------------------------------------------------
INSERT INTO ades_asignaciones_docentes (grupo_id, materia_id, profesor_id, ciclo_escolar_id)
SELECT g.id, mat.id, prof.id, g.ciclo_escolar_id
FROM tmp_esp te
CROSS JOIN tmp_pp pp
JOIN ades_grados gr  ON gr.plantel_id = pp.plantel_id AND gr.numero_grado BETWEEN te.gmin AND te.gmax
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id AND ne.nombre_nivel = 'PRIMARIA'
JOIN ades_grupos g   ON g.grado_id = gr.id                                  -- ambas secciones A y B
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id AND ce.es_vigente = TRUE
JOIN ades_materias mat ON mat.nivel_educativo_id = ne.id AND mat.nombre_materia = ANY(te.materias)
JOIN ades_materias_plan mp ON mp.materia_id = mat.id AND mp.grado_id = gr.id AND mp.ciclo_escolar_id = ce.id
JOIN ades_personas per ON per.nombre = 'Docente ' || te.nombre
   AND per.apellido_paterno = pp.nombre_plantel || ' Prim' AND per.apellido_materno = 'NVD'
JOIN ades_profesores prof ON prof.persona_id = per.id AND prof.plantel_id = pp.plantel_id
ON CONFLICT (grupo_id, materia_id, ciclo_escolar_id) DO UPDATE SET profesor_id = EXCLUDED.profesor_id;

-- -----------------------------------------------------------------------------
-- 5. ASIGNAR TITULAR a las materias TITULAR reales (Lecto, Español, Mate, etc.)
--    Solo donde la materia aplica al grado (vía plan). El titular ya existe (002).
-- -----------------------------------------------------------------------------
INSERT INTO ades_asignaciones_docentes (grupo_id, materia_id, profesor_id, ciclo_escolar_id)
SELECT g.id, mat.id, g.profesor_titular_id, g.ciclo_escolar_id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id AND ne.nombre_nivel = 'PRIMARIA'
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id AND ce.es_vigente = TRUE
JOIN ades_materias mat ON mat.nivel_educativo_id = ne.id AND mat.tipo_materia = 'TITULAR'
JOIN ades_materias_plan mp ON mp.materia_id = mat.id AND mp.grado_id = gr.id AND mp.ciclo_escolar_id = ce.id
WHERE g.profesor_titular_id IS NOT NULL
ON CONFLICT (grupo_id, materia_id, ciclo_escolar_id) DO NOTHING;

COMMIT;

-- =============================================================================
-- VERIFICACIÓN
-- =============================================================================
DO $$
DECLARE v_prof INT; v_asig INT; v_sin_doc INT;
BEGIN
  SELECT COUNT(*) INTO v_prof FROM ades_profesores WHERE numero_empleado LIKE 'EMP-ESP-PRI-%';
  SELECT COUNT(*) INTO v_asig FROM ades_asignaciones_docentes ad
    JOIN ades_materias mat ON mat.id = ad.materia_id
    JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
   WHERE ne.nombre_nivel = 'PRIMARIA';
  -- materias del plan de primaria SIN docente asignado (debe ser 0)
  SELECT COUNT(*) INTO v_sin_doc FROM ades_materias_plan mp
    JOIN ades_grados gr ON gr.id = mp.grado_id
    JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id AND ne.nombre_nivel = 'PRIMARIA'
    JOIN ades_grupos g ON g.grado_id = gr.id AND g.ciclo_escolar_id = mp.ciclo_escolar_id
    LEFT JOIN ades_asignaciones_docentes ad
      ON ad.grupo_id = g.id AND ad.materia_id = mp.materia_id AND ad.ciclo_escolar_id = g.ciclo_escolar_id
   WHERE ad.id IS NULL;
  RAISE NOTICE '=== SEED 002b (especialistas primaria) ===';
  RAISE NOTICE 'Profesores especialistas: %', v_prof;
  RAISE NOTICE 'Asignaciones primaria:    %', v_asig;
  RAISE NOTICE 'Plan sin docente (espera 0): %', v_sin_doc;
END $$;

-- =============================================================================
-- PLANTILLA reutilizable para SECUNDARIA / PREPARATORIA
-- (un docente cubre A y B y puede llevar varias materias / grados)
-- Ejemplos: "Matemáticas en 1° y 3°", "Historia de México I y II".
-- Mismo patrón: define en tmp_esp filas con materias[] y rango gmin..gmax, p.ej.:
--   ('MAT-13', 'Matemáticas 1 y 3', ARRAY['Matemáticas'], 1, 3, 'M')   -- cubre grados 1..3
--   ('HIST-MX','Historia de México I y II', ARRAY['Historia de México I','Historia de México II'], 1, 6, 'F')
-- y cambia el filtro de nivel ('SECUNDARIA' o 'PREPARATORIA') en los pasos 1-4.
-- En esos niveles NO se ejecuta el paso 5 (no hay titular).
-- =============================================================================
