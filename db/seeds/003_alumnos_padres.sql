-- =============================================================================
-- ADES — Seed 003: Alumnos, Padres de Familia e Inscripciones
-- 30 alumnos ficticios por grupo + 1 padre por alumno
-- Preparatoria Metepec: solo ciclo 26B (1er semestre), grupos A y B
-- =============================================================================
BEGIN;

-- =============================================================================
-- A. FUNCIÓN AUXILIAR: generar alumnos por grupo usando generate_series
-- Patrón nombre: "Alumno [Nivel abreviado] [Plantel abreviado] G[grado][grupo] [n]"
-- =============================================================================

-- Insertar PERSONAS de alumnos (30 por grupo)
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero, fecha_nacimiento)
SELECT
  'Alumno ' || ne_abr.abr || ' ' || pl_abr.abr ||
    ' G' || gr.numero_grado || g.nombre_grupo,
  'Num' || LPAD(n::TEXT, 2, '0'),
  'NVD',
  -- CURP ficticio único por alumno
  'XAXX' || ne_abr.abr || pl_abr.abr
    || gr.numero_grado || g.nombre_grupo
    || LPAD(n::TEXT,2,'0')
    || '00HDFNNN'
    || LPAD((ROW_NUMBER() OVER (ORDER BY pl.nombre_plantel, ne.nombre_nivel,
             gr.numero_grado, g.nombre_grupo, n))::TEXT, 3,'0') || 'A',
  CASE WHEN MOD(n,2) = 0 THEN 'F' ELSE 'M' END,
  -- Edad aproximada según nivel
  CASE
    WHEN ne.nombre_nivel = 'PRIMARIA'     THEN (CURRENT_DATE - ((5 + gr.numero_grado) * 365 + n * 10) * INTERVAL '1 day')::DATE
    WHEN ne.nombre_nivel = 'SECUNDARIA'   THEN (CURRENT_DATE - ((11 + gr.numero_grado) * 365 + n * 10) * INTERVAL '1 day')::DATE
    WHEN ne.nombre_nivel = 'PREPARATORIA' THEN (CURRENT_DATE - (15 * 365 + n * 10) * INTERVAL '1 day')::DATE
  END
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES
  (CASE WHEN ne.nombre_nivel = 'PRIMARIA' THEN 'PRI'
        WHEN ne.nombre_nivel = 'SECUNDARIA' THEN 'SEC'
        ELSE 'PREP' END)
) AS ne_abr(abr)
CROSS JOIN LATERAL (VALUES
  (CASE
    WHEN pl.nombre_plantel = 'Metepec'          THEN 'MET'
    WHEN pl.nombre_plantel = 'Tenancingo'       THEN 'TEN'
    ELSE 'IXT' END)
) AS pl_abr(abr)
CROSS JOIN generate_series(1, 30) AS n
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
ON CONFLICT (curp) DO NOTHING;

-- Insertar en ADES_ESTUDIANTES
INSERT INTO ades_estudiantes
  (matricula, persona_id, plantel_id, estatus_id, fecha_ingreso)
SELECT
  'MAT-' || ne_abr.abr || '-' || pl_abr.abr
    || '-' || gr.numero_grado || g.nombre_grupo
    || '-' || LPAD(n::TEXT,2,'0'),
  per.id,
  pl.id,
  est.id,
  ce.fecha_inicio
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES
  (CASE WHEN ne.nombre_nivel='PRIMARIA' THEN 'PRI'
        WHEN ne.nombre_nivel='SECUNDARIA' THEN 'SEC' ELSE 'PREP' END)
) AS ne_abr(abr)
CROSS JOIN LATERAL (VALUES
  (CASE WHEN pl.nombre_plantel='Metepec' THEN 'MET'
        WHEN pl.nombre_plantel='Tenancingo' THEN 'TEN' ELSE 'IXT' END)
) AS pl_abr(abr)
CROSS JOIN generate_series(1,30) AS n
JOIN ades_personas per
  ON per.nombre           = 'Alumno ' || ne_abr.abr || ' ' || pl_abr.abr
                            || ' G' || gr.numero_grado || g.nombre_grupo
 AND per.apellido_paterno = 'Num' || LPAD(n::TEXT,2,'0')
 AND per.apellido_materno = 'NVD'
CROSS JOIN ades_estatus est
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
  AND est.entidad = 'ESTUDIANTE' AND est.nombre_estatus = 'INSCRITO'
ON CONFLICT (matricula) DO NOTHING;

-- =============================================================================
-- B. INSCRIPCIONES (alumno → grupo)
-- =============================================================================
INSERT INTO ades_inscripciones
  (estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, estatus_id)
SELECT
  est_al.id,
  g.id,
  g.ciclo_escolar_id,
  ce.fecha_inicio,
  est.id
FROM ades_grupos g
JOIN ades_grados gr ON gr.id = g.grado_id
JOIN ades_planteles pl ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_ciclos_escolares ce ON ce.id = g.ciclo_escolar_id
CROSS JOIN LATERAL (VALUES
  (CASE WHEN ne.nombre_nivel='PRIMARIA' THEN 'PRI'
        WHEN ne.nombre_nivel='SECUNDARIA' THEN 'SEC' ELSE 'PREP' END)
) AS ne_abr(abr)
CROSS JOIN LATERAL (VALUES
  (CASE WHEN pl.nombre_plantel='Metepec' THEN 'MET'
        WHEN pl.nombre_plantel='Tenancingo' THEN 'TEN' ELSE 'IXT' END)
) AS pl_abr(abr)
CROSS JOIN generate_series(1,30) AS n
JOIN ades_estudiantes est_al ON est_al.matricula =
  'MAT-' || ne_abr.abr || '-' || pl_abr.abr
    || '-' || gr.numero_grado || g.nombre_grupo
    || '-' || LPAD(n::TEXT,2,'0')
CROSS JOIN ades_estatus est
WHERE ce.es_vigente = TRUE
  AND NOT (ne.nombre_nivel = 'PREPARATORIA' AND ce.nombre_ciclo = '27A')
  AND est.entidad = 'INSCRIPCION' AND est.nombre_estatus = 'VIGENTE'
ON CONFLICT (estudiante_id, grupo_id, ciclo_escolar_id) DO NOTHING;

-- =============================================================================
-- C. PADRES DE FAMILIA (1 por alumno)
-- Nombre: "Padre de [nombre alumno]"
-- =============================================================================
INSERT INTO ades_personas
  (nombre, apellido_paterno, apellido_materno, curp, genero)
SELECT
  'Padre de ' || per_al.nombre || ' ' || per_al.apellido_paterno,
  'Familia' || per_al.apellido_paterno,
  'NVD',
  'XPXX' || RIGHT(per_al.curp, 16),
  CASE WHEN MOD(est_al.id, 2) = 0 THEN 'F' ELSE 'M' END
FROM ades_estudiantes est_al
JOIN ades_personas per_al ON per_al.id = est_al.persona_id
ON CONFLICT (curp) DO NOTHING;

-- Insertar contacto familiar
INSERT INTO ades_contactos_familiares
  (estudiante_id, persona_id, parentesco, es_tutor_legal,
   es_contacto_emergencia, puede_recoger)
SELECT
  est_al.id,
  per_padre.id,
  'PADRE',
  TRUE, TRUE, TRUE
FROM ades_estudiantes est_al
JOIN ades_personas per_al ON per_al.id = est_al.persona_id
JOIN ades_personas per_padre
  ON per_padre.apellido_materno = 'NVD'
 AND per_padre.nombre = 'Padre de ' || per_al.nombre || ' ' || per_al.apellido_paterno
ON CONFLICT DO NOTHING;

-- Crear usuario para el padre (cuenta local, sin OIDC)
INSERT INTO ades_personas
  -- ya existen, no reinsertar
SELECT 1 WHERE FALSE;

-- Usuario padre: username = 'padre.' + matricula_alumno
INSERT INTO ades_usuarios
  (nombre_usuario, email_institucional, clave_hash, persona_id, rol_id, estatus_id)
SELECT
  'padre.' || est_al.matricula,
  'padre.' || LOWER(REPLACE(est_al.matricula,'-','_')) || '@familias.institutonevadi.edu.mx',
  -- Hash ficticio (se cambia en el primer login vía Authentik)
  '$argon2id$v=19$m=16,t=2,p=1$PLACEHOLDER$PLACEHOLDER_HASH',
  per_padre.id,
  rol.id,
  est_usr.id
FROM ades_estudiantes est_al
JOIN ades_personas per_al ON per_al.id = est_al.persona_id
JOIN ades_personas per_padre
  ON per_padre.apellido_materno = 'NVD'
 AND per_padre.nombre = 'Padre de ' || per_al.nombre || ' ' || per_al.apellido_paterno
CROSS JOIN ades_roles rol
CROSS JOIN ades_estatus est_usr
WHERE rol.nombre_rol = 'PADRE_FAMILIA'
  AND est_usr.entidad = 'USUARIO' AND est_usr.nombre_estatus = 'PENDIENTE'
ON CONFLICT (nombre_usuario) DO NOTHING;

-- =============================================================================
-- D. USUARIOS PARA PROFESORES (login institucional Gmail)
-- nombre_usuario = email Gmail institucional
-- Sin clave_hash (SSO vía Authentik + Google Workspace)
-- =============================================================================
INSERT INTO ades_usuarios
  (nombre_usuario, email_institucional, oidc_sub, persona_id, rol_id, estatus_id)
SELECT
  LOWER(REPLACE(REPLACE(per.nombre,' ','.'),'-','.'))
    || '.' || LOWER(LEFT(per.apellido_paterno,4))
    || '@institutonevadi.edu.mx',
  LOWER(REPLACE(REPLACE(per.nombre,' ','.'),'-','.'))
    || '.' || LOWER(LEFT(per.apellido_paterno,4))
    || '@institutonevadi.edu.mx',
  'oidc-pending-' || prof.numero_empleado,
  per.id,
  rol.id,
  est.id
FROM ades_profesores prof
JOIN ades_personas per ON per.id = prof.persona_id
CROSS JOIN ades_roles rol
CROSS JOIN ades_estatus est
WHERE rol.nombre_rol = 'DOCENTE'
  AND est.entidad = 'USUARIO' AND est.nombre_estatus = 'PENDIENTE'
ON CONFLICT (nombre_usuario) DO NOTHING;

COMMIT;

-- =============================================================================
-- VERIFICACIÓN
-- =============================================================================
DO $$
DECLARE
  v_alumnos INT; v_inscripciones INT; v_padres INT; v_usuarios INT;
BEGIN
  SELECT COUNT(*) INTO v_alumnos      FROM ades_estudiantes;
  SELECT COUNT(*) INTO v_inscripciones FROM ades_inscripciones;
  SELECT COUNT(*) INTO v_padres       FROM ades_contactos_familiares;
  SELECT COUNT(*) INTO v_usuarios     FROM ades_usuarios;
  RAISE NOTICE '=== SEED 003 ===';
  RAISE NOTICE 'Alumnos:       % (esperado: ~2820)', v_alumnos;
  RAISE NOTICE 'Inscripciones: % (esperado: ~2820)', v_inscripciones;
  RAISE NOTICE 'Padres:        % (esperado: ~2820)', v_padres;
  RAISE NOTICE 'Usuarios total:%', v_usuarios;
END $$;
