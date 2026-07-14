-- =============================================================================
-- ADES — Seed 002: Grupos y Profesores
-- Ciclo 2026-2027 Instituto Nevadi
--
-- Reglas:
--   PRIMARIA:     1 titular por grupo, 1 prof inglés por plantel (12 grupos)
--   SECUNDARIA:   1 prof por materia por plantel (cubre todos los grados/grupos)
--   PREPARATORIA: 1 prof por materia (Metepec, ciclo 26B)
-- =============================================================================
BEGIN;

-- =============================================================================
-- A. GRUPOS (A y B por cada grado activo)
-- =============================================================================
INSERT INTO ades_grupos
  (nombre_grupo, grado_id, ciclo_escolar_id,
   capacidad_maxima, turno, estatus_id)
SELECT
  g.letra, gr.id, ce.id, 30, 'MATUTINO', est.id
FROM ades_grados gr
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce   ON ce.nivel_educativo_id = ne.id AND ce.es_vigente = TRUE
JOIN ades_estatus est           ON est.entidad = 'GRUPO' AND est.nombre_estatus = 'ACTIVO',
(VALUES ('A'), ('B')) AS g(letra)
WHERE NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
ON CONFLICT (nombre_grupo, grado_id, ciclo_escolar_id) DO NOTHING;

-- =============================================================================
-- B. PERSONAS Y PROFESORES
-- =============================================================================

-- B1. Titulares primaria: 1 por grupo por plantel (6 grados x 2 grupos x 3 planteles = 36)
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero)
SELECT
  'Docente ' || pl.nombre_plantel || ' Primaria',
  'G' || gr.numero_grado || g.letra,
  'NVD',
  'XEXX' || UPPER(LEFT(REGEXP_REPLACE(pl.nombre_plantel,' ','','g'),3))
    || 'P' || gr.numero_grado || g.letra
    || 'HDFNN' || LPAD(
         (ROW_NUMBER() OVER (ORDER BY pl.nombre_plantel, gr.numero_grado, g.letra))::TEXT
       ,2,'0') || 'A',
  'M'
FROM ades_grados gr
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id,
(VALUES ('A'),('B')) AS g(letra)
WHERE ne.nombre_nivel = 'PRIMARIA'
ON CONFLICT (curp) DO NOTHING;

INSERT INTO ades_profesores
  (numero_empleado, persona_id, plantel_id, estatus_id, tipo_contrato)
SELECT
  'EMP-PRI-' || UPPER(LEFT(REGEXP_REPLACE(pl.nombre_plantel,' ','','g'),3))
    || '-G' || gr.numero_grado || g.letra,
  per.id, pl.id, est.id, 'BASE'
FROM ades_grados gr
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
CROSS JOIN (VALUES ('A'),('B')) AS g(letra)
JOIN ades_personas per
  ON per.nombre            = 'Docente ' || pl.nombre_plantel || ' Primaria'
 AND per.apellido_paterno  = 'G' || gr.numero_grado || g.letra
 AND per.apellido_materno  = 'NVD'
CROSS JOIN ades_estatus est
WHERE ne.nombre_nivel = 'PRIMARIA'
  AND est.entidad = 'PROFESOR' AND est.nombre_estatus = 'ACTIVO'
ON CONFLICT (numero_empleado) DO NOTHING;

-- B2. Inglés primaria: 1 por plantel (3 profesores)
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero)
SELECT
  'Docente Inglés ' || pl.nombre_plantel,
  'Primaria', 'NVD',
  'XEXX' || UPPER(LEFT(REGEXP_REPLACE(pl.nombre_plantel,' ','','g'),3))
    || 'INGHDFNN'
    || LPAD((ROW_NUMBER() OVER (ORDER BY pl.nombre_plantel))::TEXT,2,'0') || 'A',
  'F'
FROM ades_planteles pl
ON CONFLICT (curp) DO NOTHING;

INSERT INTO ades_profesores
  (numero_empleado, persona_id, plantel_id, estatus_id, tipo_contrato)
SELECT
  'EMP-ING-PRI-' || UPPER(LEFT(REGEXP_REPLACE(pl.nombre_plantel,' ','','g'),3)),
  per.id, pl.id, est.id, 'BASE'
FROM ades_planteles pl
JOIN ades_personas per
  ON per.nombre           = 'Docente Inglés ' || pl.nombre_plantel
 AND per.apellido_paterno = 'Primaria'
 AND per.apellido_materno = 'NVD'
CROSS JOIN ades_estatus est
WHERE est.entidad = 'PROFESOR' AND est.nombre_estatus = 'ACTIVO'
ON CONFLICT (numero_empleado) DO NOTHING;

-- B3. Profesores secundaria: 1 por materia por plantel (11 materias x 3 planteles = 33)
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero)
SELECT
  'Docente ' || mat.nombre_materia,
  pl.nombre_plantel || ' Sec', 'NVD',
  'XEXX' || UPPER(LEFT(REGEXP_REPLACE(pl.nombre_plantel,' ','','g'),3))
    || UPPER(LEFT(REGEXP_REPLACE(mat.nombre_materia,'[^A-Za-z]','','g'),3))
    || 'HDFN'
    || LPAD((ROW_NUMBER() OVER (ORDER BY pl.nombre_plantel, mat.nombre_materia))::TEXT,3,'0') || 'A',
  'M'
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
CROSS JOIN ades_planteles pl
WHERE ne.nombre_nivel = 'SECUNDARIA'
ON CONFLICT (curp) DO NOTHING;

INSERT INTO ades_profesores
  (numero_empleado, persona_id, plantel_id, estatus_id, tipo_contrato)
SELECT
  'EMP-SEC-' || UPPER(LEFT(REGEXP_REPLACE(pl.nombre_plantel,' ','','g'),3))
    || '-' || UPPER(LEFT(REGEXP_REPLACE(mat.nombre_materia,'[^A-Za-z]','','g'),4)),
  per.id, pl.id, est.id, 'BASE'
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
CROSS JOIN ades_planteles pl
JOIN ades_personas per
  ON per.nombre           = 'Docente ' || mat.nombre_materia
 AND per.apellido_paterno = pl.nombre_plantel || ' Sec'
 AND per.apellido_materno = 'NVD'
CROSS JOIN ades_estatus est
WHERE ne.nombre_nivel = 'SECUNDARIA'
  AND est.entidad = 'PROFESOR' AND est.nombre_estatus = 'ACTIVO'
ON CONFLICT (numero_empleado) DO NOTHING;

-- B4. Profesores preparatoria: 1 por materia (8 materias, solo Metepec)
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero)
SELECT
  'Docente ' || mat.nombre_materia,
  'Metepec Prep', 'NVD',
  'XEXXMET' || UPPER(LEFT(REGEXP_REPLACE(mat.nombre_materia,'[^A-Za-z]','','g'),4))
    || 'HDFN'
    || LPAD((ROW_NUMBER() OVER (ORDER BY mat.nombre_materia))::TEXT,2,'0') || 'A',
  'M'
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
WHERE ne.nombre_nivel = 'PREPARATORIA'
ON CONFLICT (curp) DO NOTHING;

INSERT INTO ades_profesores
  (numero_empleado, persona_id, plantel_id, estatus_id, tipo_contrato)
SELECT
  'EMP-PREP-MET-' || UPPER(LEFT(REGEXP_REPLACE(mat.nombre_materia,'[^A-Za-z]','','g'),4)),
  per.id, pl.id, est.id, 'BASE'
FROM ades_materias mat
JOIN ades_niveles_educativos ne ON ne.id = mat.nivel_educativo_id
JOIN ades_planteles pl ON pl.nombre_plantel = 'Metepec'
JOIN ades_personas per
  ON per.nombre           = 'Docente ' || mat.nombre_materia
 AND per.apellido_paterno = 'Metepec Prep'
 AND per.apellido_materno = 'NVD'
CROSS JOIN ades_estatus est
WHERE ne.nombre_nivel = 'PREPARATORIA'
  AND est.entidad = 'PROFESOR' AND est.nombre_estatus = 'ACTIVO'
ON CONFLICT (numero_empleado) DO NOTHING;

-- =============================================================================
-- C. ASIGNAR TITULAR A GRUPOS DE PRIMARIA
-- =============================================================================
UPDATE ades_grupos g
SET profesor_titular_id = prof.id
FROM ades_grados gr
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_profesores prof ON prof.plantel_id = pl.id
JOIN ades_personas per ON per.id = prof.persona_id
  AND per.nombre           = 'Docente ' || pl.nombre_plantel || ' Primaria'
  AND per.apellido_materno = 'NVD'
WHERE g.grado_id = gr.id
  AND ne.nombre_nivel = 'PRIMARIA'
  AND g.profesor_titular_id IS NULL
  AND per.apellido_paterno = 'G' || gr.numero_grado || g.nombre_grupo;

-- =============================================================================
-- D. ASIGNACIONES DOCENTES
-- =============================================================================

-- D1. Primaria — titular cubre todas las materias excepto inglés
INSERT INTO ades_asignaciones_docentes
  (grupo_id, materia_id, profesor_id, ciclo_escolar_id)
SELECT g.id, mat.id, g.profesor_titular_id, g.ciclo_escolar_id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_materias mat ON mat.nivel_educativo_id = ne.id AND mat.es_inglés = FALSE
WHERE ne.nombre_nivel = 'PRIMARIA' AND g.profesor_titular_id IS NOT NULL
ON CONFLICT (grupo_id, materia_id, ciclo_escolar_id) DO NOTHING;

-- D2. Primaria — inglés: prof de inglés del plantel
INSERT INTO ades_asignaciones_docentes
  (grupo_id, materia_id, profesor_id, ciclo_escolar_id)
SELECT g.id, mat.id, prof_ing.id, g.ciclo_escolar_id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_materias mat ON mat.nivel_educativo_id = ne.id AND mat.es_inglés = TRUE
JOIN ades_profesores prof_ing ON prof_ing.plantel_id = pl.id
JOIN ades_personas per_ing ON per_ing.id = prof_ing.persona_id
  AND per_ing.nombre           = 'Docente Inglés ' || pl.nombre_plantel
  AND per_ing.apellido_paterno = 'Primaria'
WHERE ne.nombre_nivel = 'PRIMARIA'
ON CONFLICT (grupo_id, materia_id, ciclo_escolar_id) DO NOTHING;

-- D3. Secundaria — 1 prof por materia por plantel, todos los grupos
INSERT INTO ades_asignaciones_docentes
  (grupo_id, materia_id, profesor_id, ciclo_escolar_id)
SELECT g.id, mat.id, prof.id, g.ciclo_escolar_id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_materias mat ON mat.nivel_educativo_id = ne.id
JOIN ades_profesores prof ON prof.plantel_id = pl.id
JOIN ades_personas per ON per.id = prof.persona_id
  AND per.nombre           = 'Docente ' || mat.nombre_materia
  AND per.apellido_paterno = pl.nombre_plantel || ' Sec'
  AND per.apellido_materno = 'NVD'
WHERE ne.nombre_nivel = 'SECUNDARIA'
ON CONFLICT (grupo_id, materia_id, ciclo_escolar_id) DO NOTHING;

-- D4. Preparatoria — 1 prof por materia, Metepec 26B
INSERT INTO ades_asignaciones_docentes
  (grupo_id, materia_id, profesor_id, ciclo_escolar_id)
SELECT g.id, mat.id, prof.id, g.ciclo_escolar_id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id AND pl.nombre_plantel = 'Metepec'
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id AND ce.nombre_ciclo = '26B'
JOIN ades_materias mat ON mat.nivel_educativo_id = ne.id
JOIN ades_profesores prof ON prof.plantel_id = pl.id
JOIN ades_personas per ON per.id = prof.persona_id
  AND per.nombre           = 'Docente ' || mat.nombre_materia
  AND per.apellido_paterno = 'Metepec Prep'
  AND per.apellido_materno = 'NVD'
WHERE ne.nombre_nivel = 'PREPARATORIA'
ON CONFLICT (grupo_id, materia_id, ciclo_escolar_id) DO NOTHING;

-- =============================================================================
-- E. USUARIO ADMIN GLOBAL INICIAL
-- =============================================================================
INSERT INTO ades_personas (nombre, apellido_paterno, apellido_materno, curp, genero)
VALUES ('Administrador','Instituto','Nevadi','XEXXADM000000HDFNN','M')
ON CONFLICT (curp) DO NOTHING;

INSERT INTO ades_usuarios
  (nombre_usuario, email_institucional, oidc_sub, persona_id, rol_id, estatus_id)
SELECT
  'admin@institutonevadi.edu.mx',
  'admin@institutonevadi.edu.mx',
  'admin-nevadi-initial',
  per.id, rol.id, est.id
FROM ades_personas per
CROSS JOIN ades_roles rol
CROSS JOIN ades_estatus est
WHERE per.curp = 'XEXXADM000000HDFNN'
  AND rol.nombre_rol = 'ADMIN_GLOBAL'
  AND est.entidad = 'USUARIO' AND est.nombre_estatus = 'ACTIVO'
ON CONFLICT (nombre_usuario) DO NOTHING;

COMMIT;

DO $$
DECLARE v_grupos INT; v_prof INT; v_asign INT;
BEGIN
  SELECT COUNT(*) INTO v_grupos FROM ades_grupos;
  SELECT COUNT(*) INTO v_prof   FROM ades_profesores;
  SELECT COUNT(*) INTO v_asign  FROM ades_asignaciones_docentes;
  RAISE NOTICE '=== SEED 002 ===';
  RAISE NOTICE 'Grupos:           %', v_grupos;
  RAISE NOTICE 'Profesores:       %', v_prof;
  RAISE NOTICE 'Asignaciones:     %', v_asign;
END $$;
