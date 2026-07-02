-- =============================================================================
-- Migración 20260630_0001: Limpieza de profesores dummy + corrección materias
-- =============================================================================
-- Acciones:
--   1. Eliminar registros dependientes de profesores dummy (horarios, asignaciones, etc.)
--   2. Eliminar profesores dummy y sus personas
--   3. Eliminar las 24 materias secundaria rotas (0 horas, sin clave, UPPERCASE)
--   4. Insertar materias secundaria correctas por grado y curso
--   5. Agregar materias Nevadi faltantes para primaria (Tabletas, Maker)
-- =============================================================================

BEGIN;

-- Desactivar FK checks para el borrado en cascada (se restaura al COMMIT/ROLLBACK)
SET session_replication_role = 'replica';

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 1: Identificar profesores dummy
-- Dummy = numero_empleado IS NOT NULL (todos los generados con DOC-XXXXX)
-- Esto incluye todos en Metepec y Tenancingo, y los fake de Ixtapan.
-- Los reales de Ixtapan (del YAML) tienen numero_empleado NULL.
-- ─────────────────────────────────────────────────────────────────────────────

-- Tabla temporal con IDs de profesores dummy y sus personas
CREATE TEMP TABLE dummy_prof AS
SELECT p.id AS prof_id, p.persona_id, pl.nombre_plantel
FROM ades_profesores p
JOIN ades_planteles pl ON p.plantel_id = pl.id
WHERE p.numero_empleado IS NOT NULL;

-- Diagnóstico antes de borrar
DO $$
DECLARE v_count INTEGER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM dummy_prof;
  RAISE NOTICE 'Profesores dummy a eliminar: %', v_count;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 2: Eliminar dependencias en el orden correcto
-- ─────────────────────────────────────────────────────────────────────────────

-- 2a. Horario indisponibilidad
DELETE FROM ades_horario_indisponibilidad
WHERE profesor_id IN (SELECT prof_id FROM dummy_prof);

-- 2b. Horarios asignados
DELETE FROM ades_horarios
WHERE profesor_id IN (SELECT prof_id FROM dummy_prof);

-- 2c. Dependencias de ades_clases antes de borrarlas
DELETE FROM ades_asignaciones_aula
WHERE clase_id IN (
  SELECT id FROM ades_clases WHERE profesor_id IN (SELECT prof_id FROM dummy_prof)
);

DELETE FROM ades_asistencias
WHERE clase_id IN (
  SELECT id FROM ades_clases WHERE profesor_id IN (SELECT prof_id FROM dummy_prof)
);

DELETE FROM ades_avance_planificacion
WHERE clase_id IN (
  SELECT id FROM ades_clases WHERE profesor_id IN (SELECT prof_id FROM dummy_prof)
);

-- 2c. Clases asignadas a profesor directamente
DELETE FROM ades_clases
WHERE profesor_id IN (SELECT prof_id FROM dummy_prof);

-- 2d. Evaluación docente 360°
DELETE FROM ades_evaluacion_docente
WHERE profesor_id IN (SELECT prof_id FROM dummy_prof);

-- 2e. Disponibilidad docente registrada
DELETE FROM ades_disponibilidad_docente
WHERE profesor_id IN (SELECT prof_id FROM dummy_prof);

-- 2f. Reportes de conducta generados por el docente
DELETE FROM ades_reportes_conducta
WHERE reportado_por_id IN (SELECT prof_id FROM dummy_prof);

-- 2g. Asignaciones docentes (materia-grupo-ciclo)
DELETE FROM ades_asignaciones_docentes
WHERE profesor_id IN (SELECT prof_id FROM dummy_prof);

-- 2h. Grupos con profesor_titular dummy → anular FK
UPDATE ades_grupos
SET profesor_titular_id = NULL
WHERE profesor_titular_id IN (SELECT prof_id FROM dummy_prof);

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 3: Eliminar profesores dummy y sus personas
-- ─────────────────────────────────────────────────────────────────────────────

-- 3a. Usuarios vinculados (si existen)
DELETE FROM ades_usuarios
WHERE persona_id IN (SELECT persona_id FROM dummy_prof);

-- 3b. Expediente laboral / RRHH (usa persona_id)
DELETE FROM ades_expediente_laboral WHERE persona_id IN (SELECT persona_id FROM dummy_prof);

-- 3c. Capacitaciones
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='ades_capacitaciones_docentes') THEN
    EXECUTE 'DELETE FROM ades_capacitaciones_docentes WHERE profesor_id IN (SELECT prof_id FROM dummy_prof)';
  END IF;
END $$;

-- 3d. Licencias docentes
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='ades_licencias_docentes') THEN
    EXECUTE 'DELETE FROM ades_licencias_docentes WHERE profesor_id IN (SELECT prof_id FROM dummy_prof)';
  END IF;
END $$;

-- 3e. Asistencias docentes
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='ades_asistencias_docentes') THEN
    EXECUTE 'DELETE FROM ades_asistencias_docentes WHERE profesor_id IN (SELECT prof_id FROM dummy_prof)';
  END IF;
END $$;

-- 3f. Foros/observaciones: omitido — FK bypass activo, datos dummy se eliminan con personas

-- 3h. Eliminar registros de ades_profesores
DELETE FROM ades_profesores
WHERE id IN (SELECT prof_id FROM dummy_prof);

-- 3i. Eliminar personas dummy (solo las que ya no tienen otras referencias)
DELETE FROM ades_personas
WHERE id IN (SELECT persona_id FROM dummy_prof)
  AND id NOT IN (SELECT persona_id FROM ades_profesores WHERE persona_id IS NOT NULL)
  AND id NOT IN (SELECT persona_id FROM ades_estudiantes WHERE persona_id IS NOT NULL)
  AND id NOT IN (SELECT persona_id FROM ades_tutores_alumnos WHERE persona_id IS NOT NULL)
  AND id NOT IN (SELECT persona_id FROM ades_personal_administrativo WHERE persona_id IS NOT NULL)
  AND id NOT IN (SELECT persona_id FROM ades_personal_salud WHERE persona_id IS NOT NULL);

DROP TABLE dummy_prof;

DO $$ BEGIN RAISE NOTICE '✓ Profesores dummy eliminados'; END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 4: Eliminar las 24 materias secundaria rotas (UPPERCASE, 0 horas, sin clave)
-- ─────────────────────────────────────────────────────────────────────────────

-- Primero: quitar de materias_plan
DELETE FROM ades_materias_plan
WHERE materia_id IN (
  SELECT m.id FROM ades_materias m
  JOIN ades_niveles_educativos ne ON m.nivel_educativo_id = ne.id
  WHERE ne.nombre_nivel = 'SECUNDARIA'
    AND (m.clave_materia IS NULL OR m.clave_materia = '')
    AND m.horas_semana = 0
);

-- Después: quitar materias rotas
DELETE FROM ades_materias
WHERE id IN (
  SELECT m.id FROM ades_materias m
  JOIN ades_niveles_educativos ne ON m.nivel_educativo_id = ne.id
  WHERE ne.nombre_nivel = 'SECUNDARIA'
    AND (m.clave_materia IS NULL OR m.clave_materia = '')
    AND m.horas_semana = 0
);

DO $$ BEGIN RAISE NOTICE '✓ Materias secundaria rotas eliminadas'; END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 5: Insertar materias secundaria correctas
-- Aplica el plan real de Nevadi Secundaria (igual en los 3 planteles)
-- Bloques de 50 min. Naming oficial Nevadi/SEP NEM secundaria.
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
  sec_nivel_id UUID := '019e8f74-d13f-77e5-aeb8-e859b106072c';
BEGIN

-- Materias por grado (Inglés I/II/III, Matemáticas I/II/III, Ciencias por rama)
INSERT INTO ades_materias
  (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, tipo_materia,
   campo_formativo, reporta_a_sep_uaemex, incluir_en_boleta, codigo_sep, es_inglés)
VALUES
  -- Inglés por grado (5 bloques/semana; SEP NEM reporta como "Lengua Extranjera")
  ('Inglés I',      'SEC-ING-1',   sec_nivel_id, 5.0, 'OFICIAL_SEP_SECUNDARIA', 'LENGUAJES',                     true, true, 'LE-1',  true),
  ('Inglés II',     'SEC-ING-2',   sec_nivel_id, 5.0, 'OFICIAL_SEP_SECUNDARIA', 'LENGUAJES',                     true, true, 'LE-2',  true),
  ('Inglés III',    'SEC-ING-3',   sec_nivel_id, 5.0, 'OFICIAL_SEP_SECUNDARIA', 'LENGUAJES',                     true, true, 'LE-3',  true),

  -- Matemáticas por grado (5 bloques/semana)
  ('Matemáticas I',   'SEC-MAT-1', sec_nivel_id, 5.0, 'OFICIAL_SEP_SECUNDARIA', 'SABERES_PENSAMIENTO_CIENTIFICO', true, true, 'MAT-1', false),
  ('Matemáticas II',  'SEC-MAT-2', sec_nivel_id, 5.0, 'OFICIAL_SEP_SECUNDARIA', 'SABERES_PENSAMIENTO_CIENTIFICO', true, true, 'MAT-2', false),
  ('Matemáticas III', 'SEC-MAT-3', sec_nivel_id, 5.0, 'OFICIAL_SEP_SECUNDARIA', 'SABERES_PENSAMIENTO_CIENTIFICO', true, true, 'MAT-3', false),

  -- Ciencias por rama (Biología 1°=4h, Física 2°=6h, Química 3°=6h)
  ('Ciencias (Biología)', 'SEC-CNT-BIO', sec_nivel_id, 4.0, 'OFICIAL_SEP_SECUNDARIA', 'SABERES_PENSAMIENTO_CIENTIFICO', true, true, 'CNT-B', false),
  ('Ciencias (Física)',   'SEC-CNT-FIS', sec_nivel_id, 6.0, 'OFICIAL_SEP_SECUNDARIA', 'SABERES_PENSAMIENTO_CIENTIFICO', true, true, 'CNT-F', false),
  ('Ciencias (Química)',  'SEC-CNT-QUI', sec_nivel_id, 6.0, 'OFICIAL_SEP_SECUNDARIA', 'SABERES_PENSAMIENTO_CIENTIFICO', true, true, 'CNT-Q', false),

  -- Historia (horas varían: 1°=2, 2°/3°=4; se usa campo formativo por grado en plan)
  ('Historia',            'SEC-HIS-N',  sec_nivel_id, 4.0, 'OFICIAL_SEP_SECUNDARIA', 'ETICA_NATURALEZA_SOCIEDADES',   true, true, 'HIS',   false),

  -- Geografía (solo 1°, 4 bloques/sem)
  ('Geografía',           'SEC-GEO-N',  sec_nivel_id, 4.0, 'OFICIAL_SEP_SECUNDARIA', 'ETICA_NATURALEZA_SOCIEDADES',   true, true, 'GEO',   false),

  -- Educación Ambiental (todos los grados, 2 bloques)
  ('Edu. Ambiental',      'SEC-ENV',    sec_nivel_id, 2.0, 'OFICIAL_SEP_SECUNDARIA', 'ETICA_NATURALEZA_SOCIEDADES',   true, true, 'ENV',   false),

  -- TLEC (Taller de Lectura, Escritura y Comunicación, 1 bloque)
  ('TLEC',                'SEC-TLEC',   sec_nivel_id, 1.0, 'OFICIAL_SEP_SECUNDARIA', 'LENGUAJES',                     true, true, 'TLEC',  false),

  -- Igualdad de Género (1 bloque, todos los grados — programa SEP NEM 2022)
  ('Igualdad de Género',  'SEC-IG',     sec_nivel_id, 1.0, 'OFICIAL_SEP_SECUNDARIA', 'ETICA_NATURALEZA_SOCIEDADES',   true, true, 'IG',    false),

  -- Maker (2 bloques — Proyectos maker/STEM, plan Nevadi dentro del campo tecnología)
  ('Maker',               'SEC-MKR',    sec_nivel_id, 2.0, 'NEVADI_FORMATIVA',       'HUMANO_COMUNITARIO',            false, true, NULL,   false),

  -- Educación Financiera (1 bloque — programa SEP / Nevadi institucional)
  ('Edu. Financiera',     'SEC-EFIN',   sec_nivel_id, 1.0, 'NEVADI_FORMATIVA',       'HUMANO_COMUNITARIO',            false, true, NULL,   false),

  -- Educación Socioemocional (medio bloque, A+B juntos por grado; Emma)
  ('Socioemocional',      'SEC-SOC',    sec_nivel_id, 0.5, 'OFICIAL_SEP_SECUNDARIA', 'HUMANO_COMUNITARIO',            true, true, 'SOC',   false),

  -- Proyectos Escolares (2 medios bloques/semana ante salida; Coordinación)
  ('Proyectos',           'SEC-PRY',    sec_nivel_id, 1.0, 'NEVADI_FORMATIVA',       'HUMANO_COMUNITARIO',            false, true, NULL,   false)

ON CONFLICT (nombre_materia, nivel_educativo_id) DO UPDATE SET
  clave_materia        = EXCLUDED.clave_materia,
  horas_semana         = EXCLUDED.horas_semana,
  tipo_materia         = EXCLUDED.tipo_materia,
  campo_formativo      = EXCLUDED.campo_formativo,
  reporta_a_sep_uaemex = EXCLUDED.reporta_a_sep_uaemex,
  codigo_sep           = EXCLUDED.codigo_sep;

END $$;

DO $$ BEGIN RAISE NOTICE '✓ Materias secundaria correctas insertadas/actualizadas'; END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 6: Actualizar materias secundaria genéricas existentes con horas correctas
-- Las genéricas (SEC-ESP, SEC-MAT, etc.) quedan como referencia NEM
-- pero se corrigen horas donde divergen del plan real Nevadi
-- ─────────────────────────────────────────────────────────────────────────────

-- Historia genérica: cambiar a 4h (promedio real; por grado varía 2/4/4)
UPDATE ades_materias SET horas_semana = 4.0
WHERE clave_materia = 'SEC-HIS' AND horas_semana != 4.0;

-- Inglés genérico: Nevadi da 5h no 3h
UPDATE ades_materias SET horas_semana = 5.0
WHERE clave_materia = 'SEC-ING' AND horas_semana != 5.0;

-- Ciencias genérico: marcar como referencia, el plan real usa las específicas
UPDATE ades_materias SET horas_semana = 5.0  -- promedio (4+6+6)/3
WHERE clave_materia = 'SEC-CNT' AND horas_semana != 5.0;

DO $$ BEGIN RAISE NOTICE '✓ Materias secundaria genéricas corregidas'; END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 7: Agregar materias Nevadi faltantes para PRIMARIA
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
  pri_nivel_id UUID := '019e8f74-d13f-7052-9890-b128df7ea199';
BEGIN

INSERT INTO ades_materias
  (nombre_materia, clave_materia, nivel_educativo_id, horas_semana, tipo_materia,
   campo_formativo, reporta_a_sep_uaemex, incluir_en_boleta, es_inglés)
VALUES
  -- Tabletas (iPad): uso pedagógico de dispositivos — Nevadi desde 1°
  ('Tabletas',   'NVI-PRI-TAB', pri_nivel_id, 1.0, 'NEVADI_FORMATIVA',
   'HUMANO_COMUNITARIO', false, true, false),

  -- Maker Primaria: proyectos STEM hands-on — Nevadi 3°-6°
  ('Maker',      'NVI-PRI-MKR', pri_nivel_id, 1.0, 'NEVADI_ENRIQUECIMIENTO',
   'HUMANO_COMUNITARIO', false, true, false),

  -- Desarrollo Comunitario (especialista pool, mencionado en YAML)
  ('Desarrollo Comunitario', 'PRI-DC', pri_nivel_id, 1.0, 'OFICIAL_SEP_PRIMARIA',
   'HUMANO_COMUNITARIO', true, true, false),

  -- Fábrica de Lectura (sesiones cortas 30 min, mencionado en YAML)
  ('Fábrica de Lectura', 'NVI-PRI-FAB', pri_nivel_id, 1.0, 'NEVADI_FORMATIVA',
   'LENGUAJES', false, false, false),

  -- Ortografía (sesiones cortas 30 min, mencionado en YAML)
  ('Ortografía', 'NVI-PRI-ORT', pri_nivel_id, 1.0, 'NEVADI_FORMATIVA',
   'LENGUAJES', false, false, false),

  -- Proyectos (primaria — en la tarde, mencionado en YAML)
  ('Proyectos',  'PRI-PRY', pri_nivel_id, 2.0, 'OFICIAL_SEP_PRIMARIA',
   'HUMANO_COMUNITARIO', true, true, false),

  -- Socioemocional (especialista, mencionado en YAML primaria)
  ('Socioemocional', 'PRI-SOC', pri_nivel_id, 1.0, 'OFICIAL_SEP_PRIMARIA',
   'HUMANO_COMUNITARIO', true, true, false)

ON CONFLICT (nombre_materia, nivel_educativo_id) DO UPDATE SET
  clave_materia        = EXCLUDED.clave_materia,
  horas_semana         = EXCLUDED.horas_semana,
  tipo_materia         = EXCLUDED.tipo_materia,
  campo_formativo      = EXCLUDED.campo_formativo,
  reporta_a_sep_uaemex = EXCLUDED.reporta_a_sep_uaemex;

END $$;

DO $$ BEGIN RAISE NOTICE '✓ Materias Nevadi primaria agregadas'; END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 8: Limpiar duplicados en ades_materias_plan (mantener registro más reciente)
-- ─────────────────────────────────────────────────────────────────────────────

-- Eliminar duplicados preservando el id con fecha_modificacion más reciente
DELETE FROM ades_materias_plan
WHERE id IN (
  SELECT id FROM (
    SELECT id,
           ROW_NUMBER() OVER (
             PARTITION BY materia_id, grado_id, ciclo_escolar_id
             ORDER BY fecha_modificacion DESC, id DESC
           ) AS rn
    FROM ades_materias_plan
  ) ranked
  WHERE rn > 1
);

DO $$ BEGIN RAISE NOTICE '✓ Duplicados en ades_materias_plan eliminados'; END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- PASO 9: Asegurar auditoría en tablas nuevas/modificadas
-- ─────────────────────────────────────────────────────────────────────────────

SELECT auditoria.asignar_biu('public.ades_materias');

-- Restaurar FK checks
SET session_replication_role = 'origin';

-- ─────────────────────────────────────────────────────────────────────────────
-- VERIFICACIÓN FINAL
-- ─────────────────────────────────────────────────────────────────────────────

DO $$
DECLARE
  v_profs_dummy   INTEGER;
  v_mat_sec       INTEGER;
  v_mat_pri_nevi  INTEGER;
  v_plan_dups     INTEGER;
BEGIN
  -- Profesores dummy restantes
  SELECT COUNT(*) INTO v_profs_dummy
  FROM ades_profesores WHERE numero_empleado IS NOT NULL;

  -- Materias secundaria totales (esperamos ~29: 11 genéricas + 18 nuevas)
  SELECT COUNT(*) INTO v_mat_sec
  FROM ades_materias m
  JOIN ades_niveles_educativos ne ON m.nivel_educativo_id = ne.id
  WHERE ne.nombre_nivel = 'SECUNDARIA' AND m.is_active = true;

  -- Materias Nevadi primaria (esperamos >= 9)
  SELECT COUNT(*) INTO v_mat_pri_nevi
  FROM ades_materias m
  JOIN ades_niveles_educativos ne ON m.nivel_educativo_id = ne.id
  WHERE ne.nombre_nivel = 'PRIMARIA' AND m.tipo_materia LIKE 'NEVADI%';

  -- Duplicados restantes en plan
  SELECT COUNT(*) INTO v_plan_dups
  FROM (
    SELECT materia_id, grado_id, ciclo_escolar_id, COUNT(*)
    FROM ades_materias_plan
    GROUP BY materia_id, grado_id, ciclo_escolar_id
    HAVING COUNT(*) > 1
  ) sub;

  RAISE NOTICE '=== VERIFICACIÓN FINAL ===';
  RAISE NOTICE '  Profesores dummy restantes:  %  (esperado 0)', v_profs_dummy;
  RAISE NOTICE '  Materias secundaria activas: %  (esperado ~29)', v_mat_sec;
  RAISE NOTICE '  Materias Nevadi primaria:    %  (esperado >=9)', v_mat_pri_nevi;
  RAISE NOTICE '  Duplicados materias_plan:    %  (esperado 0)', v_plan_dups;
END $$;

COMMIT;
