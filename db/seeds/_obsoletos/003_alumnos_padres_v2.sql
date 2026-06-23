-- =============================================================================
-- ADES — Seed 003 CORREGIDO: Alumnos, Padres e Inscripciones
-- CURP exactamente 18 chars:
--   Alumnos: XAAL + nivel(2) + plantel(2) + grado(1) + grupo(1) + seq(6) + A + 1
--   Padres:  XAPA + mismo patrón con P al inicio
-- =============================================================================
BEGIN;

-- =============================================================================
-- A. PERSONAS DE ALUMNOS (30 por grupo)
-- =============================================================================
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero, fecha_nacimiento)
SELECT
  'Alumno ' || ne_abr || ' ' || pl_abr || ' G' || gr.numero_grado || g.nombre_grupo,
  'N' || LPAD(n::TEXT, 2, '0'),
  'NVD',
  -- CURP exactamente 18: XAAL + 2(niv) + 2(pla) + 1(grado) + 1(grp) + 4(seq) + 2(fijo)
  'XAAL' || ne_abr || pl_abr ||
    gr.numero_grado::TEXT || g.nombre_grupo ||
    LPAD(n::TEXT, 4, '0') || 'A1',
  CASE WHEN MOD(n, 2) = 0 THEN 'F' ELSE 'M' END,
  (CURRENT_DATE - (
    CASE ne.nombre_nivel
      WHEN 'PRIMARIA'     THEN (5 + gr.numero_grado) * 365
      WHEN 'SECUNDARIA'   THEN (11 + gr.numero_grado) * 365
      ELSE 15 * 365
    END + n * 7
  ) * INTERVAL '1 day')::DATE
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES (
  CASE ne.nombre_nivel
    WHEN 'PRIMARIA' THEN 'PR'
    WHEN 'SECUNDARIA' THEN 'SE'
    ELSE 'PP' END
)) AS t1(ne_abr)
CROSS JOIN LATERAL (VALUES (
  CASE pl.nombre_plantel
    WHEN 'Metepec'          THEN 'ME'
    WHEN 'Tenancingo'       THEN 'TE'
    ELSE 'IX' END
)) AS t2(pl_abr)
CROSS JOIN generate_series(1, 30) AS n
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
ON CONFLICT (curp) DO NOTHING;

-- =============================================================================
-- B. ESTUDIANTES
-- =============================================================================
INSERT INTO ades_estudiantes
  (matricula, persona_id, plantel_id, estatus_id, fecha_ingreso)
SELECT
  'MAT-' || ne_abr || '-' || pl_abr || '-' || gr.numero_grado || g.nombre_grupo
    || '-' || LPAD(n::TEXT, 2, '0'),
  per.id,
  pl.id,
  est.id,
  ce.fecha_inicio
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES (
  CASE ne.nombre_nivel WHEN 'PRIMARIA' THEN 'PR' WHEN 'SECUNDARIA' THEN 'SE' ELSE 'PP' END
)) AS t1(ne_abr)
CROSS JOIN LATERAL (VALUES (
  CASE pl.nombre_plantel WHEN 'Metepec' THEN 'ME' WHEN 'Tenancingo' THEN 'TE' ELSE 'IX' END
)) AS t2(pl_abr)
CROSS JOIN generate_series(1, 30) AS n
JOIN ades_personas per
  ON per.curp = 'XAAL' || ne_abr || pl_abr ||
     gr.numero_grado::TEXT || g.nombre_grupo ||
     LPAD(n::TEXT, 4, '0') || 'A1'
CROSS JOIN ades_estatus est
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
  AND est.entidad = 'ESTUDIANTE' AND est.nombre_estatus = 'INSCRITO'
ON CONFLICT (matricula) DO NOTHING;

-- =============================================================================
-- C. INSCRIPCIONES
-- =============================================================================
INSERT INTO ades_inscripciones
  (estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, estatus_id)
SELECT
  est_al.id, g.id, g.ciclo_escolar_id, ce.fecha_inicio, est.id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES (
  CASE ne.nombre_nivel WHEN 'PRIMARIA' THEN 'PR' WHEN 'SECUNDARIA' THEN 'SE' ELSE 'PP' END
)) AS t1(ne_abr)
CROSS JOIN LATERAL (VALUES (
  CASE pl.nombre_plantel WHEN 'Metepec' THEN 'ME' WHEN 'Tenancingo' THEN 'TE' ELSE 'IX' END
)) AS t2(pl_abr)
CROSS JOIN generate_series(1, 30) AS n
JOIN ades_estudiantes est_al ON est_al.matricula =
  'MAT-' || ne_abr || '-' || pl_abr || '-' || gr.numero_grado || g.nombre_grupo
  || '-' || LPAD(n::TEXT, 2, '0')
CROSS JOIN ades_estatus est
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
  AND est.entidad = 'INSCRIPCION' AND est.nombre_estatus = 'VIGENTE'
ON CONFLICT (estudiante_id, grupo_id, ciclo_escolar_id) DO NOTHING;

-- =============================================================================
-- D. PADRES DE FAMILIA
-- CURP padre: XAPA + mismo ne_abr+pl_abr+grado+grupo+seq + A1
-- =============================================================================
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero)
SELECT
  'Padre de Alumno ' || ne_abr || ' ' || pl_abr || ' G' || gr.numero_grado || g.nombre_grupo,
  'FamN' || LPAD(n::TEXT, 2, '0'),
  'NVD',
  'XAPA' || ne_abr || pl_abr ||
    gr.numero_grado::TEXT || g.nombre_grupo ||
    LPAD(n::TEXT, 4, '0') || 'A1',
  CASE WHEN MOD(n, 2) = 0 THEN 'F' ELSE 'M' END
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES (
  CASE ne.nombre_nivel WHEN 'PRIMARIA' THEN 'PR' WHEN 'SECUNDARIA' THEN 'SE' ELSE 'PP' END
)) AS t1(ne_abr)
CROSS JOIN LATERAL (VALUES (
  CASE pl.nombre_plantel WHEN 'Metepec' THEN 'ME' WHEN 'Tenancingo' THEN 'TE' ELSE 'IX' END
)) AS t2(pl_abr)
CROSS JOIN generate_series(1, 30) AS n
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
ON CONFLICT (curp) DO NOTHING;

-- Contactos familiares
INSERT INTO ades_contactos_familiares
  (estudiante_id, persona_id, parentesco, es_tutor_legal,
   es_contacto_emergencia, puede_recoger)
SELECT
  est_al.id, per_padre.id, 'PADRE', TRUE, TRUE, TRUE
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES (
  CASE ne.nombre_nivel WHEN 'PRIMARIA' THEN 'PR' WHEN 'SECUNDARIA' THEN 'SE' ELSE 'PP' END
)) AS t1(ne_abr)
CROSS JOIN LATERAL (VALUES (
  CASE pl.nombre_plantel WHEN 'Metepec' THEN 'ME' WHEN 'Tenancingo' THEN 'TE' ELSE 'IX' END
)) AS t2(pl_abr)
CROSS JOIN generate_series(1, 30) AS n
JOIN ades_estudiantes est_al ON est_al.matricula =
  'MAT-' || ne_abr || '-' || pl_abr || '-' || gr.numero_grado || g.nombre_grupo
  || '-' || LPAD(n::TEXT, 2, '0')
JOIN ades_personas per_padre ON per_padre.curp =
  'XAPA' || ne_abr || pl_abr ||
  gr.numero_grado::TEXT || g.nombre_grupo ||
  LPAD(n::TEXT, 4, '0') || 'A1'
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- E. USUARIOS PARA PADRES (cuenta local pendiente de activación)
-- =============================================================================
INSERT INTO ades_usuarios
  (nombre_usuario, email_institucional, clave_hash, persona_id, rol_id, estatus_id)
SELECT
  'padre.' || est_al.matricula,
  'padre.' || LOWER(REPLACE(est_al.matricula, '-', '_'))
    || '@familias.institutonevadi.edu.mx',
  '$argon2id$v=19$m=16,t=2,p=1$PLACEHOLDER$CHANGEONFIRSTLOGIN',
  per_padre.id, rol.id, est_usr.id
FROM ades_estudiantes est_al
JOIN ades_personas per_al ON per_al.id = est_al.persona_id
JOIN ades_personas per_padre
  ON per_padre.curp = REPLACE(per_al.curp, 'XAAL', 'XAPA')
CROSS JOIN ades_roles rol
CROSS JOIN ades_estatus est_usr
WHERE rol.nombre_rol = 'PADRE_FAMILIA'
  AND est_usr.entidad = 'USUARIO' AND est_usr.nombre_estatus = 'PENDIENTE'
ON CONFLICT (nombre_usuario) DO NOTHING;

-- =============================================================================
-- F. USUARIOS PARA PROFESORES (SSO Gmail institucional)
-- =============================================================================
INSERT INTO ades_usuarios
  (nombre_usuario, email_institucional, oidc_sub, persona_id, rol_id, estatus_id)
SELECT
  LOWER(
    LEFT(REGEXP_REPLACE(per.nombre, '[^A-Za-z ]', '', 'g'), 10) || '.' ||
    LEFT(REGEXP_REPLACE(per.apellido_paterno, '[^A-Za-z]', '', 'g'), 8)
  ) || '@institutonevadi.edu.mx',
  LOWER(
    LEFT(REGEXP_REPLACE(per.nombre, '[^A-Za-z ]', '', 'g'), 10) || '.' ||
    LEFT(REGEXP_REPLACE(per.apellido_paterno, '[^A-Za-z]', '', 'g'), 8)
  ) || '@institutonevadi.edu.mx',
  'oidc-pending-' || prof.numero_empleado,
  per.id, rol.id, est.id
FROM ades_profesores prof
JOIN ades_personas per ON per.id = prof.persona_id
CROSS JOIN ades_roles rol
CROSS JOIN ades_estatus est
WHERE rol.nombre_rol = 'DOCENTE'
  AND est.entidad = 'USUARIO' AND est.nombre_estatus = 'PENDIENTE'
ON CONFLICT (nombre_usuario) DO NOTHING;

COMMIT;

DO $$
DECLARE v_alumnos INT; v_insc INT; v_padres INT; v_usr INT;
BEGIN
  SELECT COUNT(*) INTO v_alumnos FROM ades_estudiantes;
  SELECT COUNT(*) INTO v_insc    FROM ades_inscripciones;
  SELECT COUNT(*) INTO v_padres  FROM ades_contactos_familiares;
  SELECT COUNT(*) INTO v_usr     FROM ades_usuarios;
  RAISE NOTICE '=== SEED 003 ===';
  RAISE NOTICE 'Alumnos:       %', v_alumnos;
  RAISE NOTICE 'Inscripciones: %', v_insc;
  RAISE NOTICE 'Padres:        %', v_padres;
  RAISE NOTICE 'Usuarios:      %', v_usr;
END $$;
