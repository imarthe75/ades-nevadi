-- 167_cohorte_prepa_metepec_coherente.sql
-- Hace que la cohorte demo de Preparatoria Metepec sea UNA sola progresando en el tiempo
-- (usuario 2026-07-22: "que la demo sea la misma"). El generador había creado 3 conjuntos
-- de alumnos DISJUNTOS para 25B-1º, 25B-2º y 26B-3º. Modelo semestral correcto: los MISMOS
-- 52 alumnos (los de 26B-3º, vigente) cursaron 1º en 25B, 2º en 26A y 3º en 26B.
--
-- Los ~104 alumnos demo redundantes (25B-1º y 25B-2º viejos) se ELIMINAN FÍSICAMENTE por
-- completo (usuario: "si puedes mejor realiza el borrado completo"; datos de prueba, con
-- backup pre-cohorte). Cada alumno tiene cuenta ades_usuarios; el árbol se verificó ACOTADO:
--   estudiante → 26 hijos con datos (+ nieto ades_calificaciones_tareas)
--   usuario    → 4 hojas (notificaciones, usuario_roles, notificaciones_sistema, acuses)
--   persona    → hijos directos (usuarios, contactos, direcciones, …)
-- Se usa session_replication_role=replica para desactivar la verificación de FK durante el
-- borrado (orden irrelevante, sin errores) y se borran TODOS los descendientes para no dejar
-- huérfanos. Se reactiva antes de los INSERT (para que audit_biu asigne ref/row_version).

BEGIN;

-- ============ 1. Conjuntos de trabajo ============
CREATE TEMP TABLE _cohorte ON COMMIT DROP AS
SELECT i.estudiante_id, gr.nombre_grupo
FROM ades_inscripciones i
JOIN ades_grupos gr ON gr.id = i.grupo_id
JOIN ades_grados g ON g.id = gr.grado_id
JOIN ades_planteles p ON p.id = g.plantel_id
JOIN ades_ciclos_escolares c ON c.id = gr.ciclo_escolar_id
JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
WHERE n.nombre_nivel = 'PREPARATORIA' AND p.nombre_plantel = 'Metepec'
  AND c.nombre_ciclo = '26B' AND g.numero_grado = 3;

CREATE TEMP TABLE _est_del ON COMMIT DROP AS
SELECT DISTINCT i.estudiante_id AS id
FROM ades_inscripciones i
JOIN ades_grupos gr ON gr.id = i.grupo_id
JOIN ades_grados g ON g.id = gr.grado_id
JOIN ades_planteles p ON p.id = g.plantel_id
JOIN ades_ciclos_escolares c ON c.id = gr.ciclo_escolar_id
JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
WHERE n.nombre_nivel = 'PREPARATORIA' AND p.nombre_plantel = 'Metepec'
  AND c.nombre_ciclo = '25B' AND g.numero_grado IN (1, 2)
  AND i.estudiante_id NOT IN (SELECT estudiante_id FROM _cohorte);  -- jamás tocar cohorte

CREATE TEMP TABLE _per_del ON COMMIT DROP AS
SELECT persona_id AS id FROM ades_estudiantes WHERE id IN (SELECT id FROM _est_del);

CREATE TEMP TABLE _usr_del ON COMMIT DROP AS
SELECT id FROM ades_usuarios WHERE persona_id IN (SELECT id FROM _per_del);

CREATE TEMP TABLE _grp2del ON COMMIT DROP AS
SELECT gr.id AS grupo_id
FROM ades_grupos gr
JOIN ades_grados g ON g.id = gr.grado_id
JOIN ades_planteles p ON p.id = g.plantel_id
JOIN ades_ciclos_escolares c ON c.id = gr.ciclo_escolar_id
JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
WHERE n.nombre_nivel = 'PREPARATORIA' AND p.nombre_plantel = 'Metepec'
  AND c.nombre_ciclo = '25B' AND g.numero_grado = 2;

-- ============ 2. Borrado físico (FK desactivadas, todos los descendientes) ============
SET LOCAL session_replication_role = replica;

-- nieto: calificaciones de las entregas de los alumnos a borrar
DELETE FROM ades_calificaciones_tareas WHERE tarea_entrega_id IN
  (SELECT id FROM ades_tareas_entregas WHERE estudiante_id IN (SELECT id FROM _est_del));

-- hijos directos de ades_estudiantes
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT DISTINCT c.conrelid::regclass AS child, att.attname AS col
           FROM pg_constraint c JOIN unnest(c.conkey) AS k(attnum) ON true
           JOIN pg_attribute att ON att.attrelid=c.conrelid AND att.attnum=k.attnum
           WHERE c.confrelid='ades_estudiantes'::regclass AND c.contype='f'
  LOOP EXECUTE format('DELETE FROM %s WHERE %I IN (SELECT id FROM _est_del)', r.child, r.col); END LOOP;
END $$;

-- hijos directos de ades_usuarios (4 hojas)
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT DISTINCT c.conrelid::regclass AS child, att.attname AS col
           FROM pg_constraint c JOIN unnest(c.conkey) AS k(attnum) ON true
           JOIN pg_attribute att ON att.attrelid=c.conrelid AND att.attnum=k.attnum
           WHERE c.confrelid='ades_usuarios'::regclass AND c.contype='f'
  LOOP EXECUTE format('DELETE FROM %s WHERE %I IN (SELECT id FROM _usr_del)', r.child, r.col); END LOOP;
END $$;

-- hijos directos de ades_personas (incluye ades_usuarios, contactos, direcciones, …)
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT DISTINCT c.conrelid::regclass AS child, att.attname AS col
           FROM pg_constraint c JOIN unnest(c.conkey) AS k(attnum) ON true
           JOIN pg_attribute att ON att.attrelid=c.conrelid AND att.attnum=k.attnum
           WHERE c.confrelid='ades_personas'::regclass AND c.contype='f'
  LOOP EXECUTE format('DELETE FROM %s WHERE %I IN (SELECT id FROM _per_del)', r.child, r.col); END LOOP;
END $$;

-- subárbol de grupos 25B-2º (foros→mensajes→respuestas + resto)
DELETE FROM ades_respuestas_foro WHERE mensaje_id IN (
  SELECT m.id FROM ades_mensajes_foro m
  WHERE m.foro_id IN (SELECT id FROM ades_foros WHERE grupo_id IN (SELECT grupo_id FROM _grp2del)));
DELETE FROM ades_mensajes_foro WHERE foro_id IN
  (SELECT id FROM ades_foros WHERE grupo_id IN (SELECT grupo_id FROM _grp2del));
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN SELECT DISTINCT c.conrelid::regclass AS child, att.attname AS col
           FROM pg_constraint c JOIN unnest(c.conkey) AS k(attnum) ON true
           JOIN pg_attribute att ON att.attrelid=c.conrelid AND att.attnum=k.attnum
           WHERE c.confrelid='ades_grupos'::regclass AND c.contype='f'
  LOOP EXECUTE format('DELETE FROM %s WHERE %I IN (SELECT grupo_id FROM _grp2del)', r.child, r.col); END LOOP;
END $$;

-- raíces
DELETE FROM ades_usuarios    WHERE id IN (SELECT id FROM _usr_del);
DELETE FROM ades_estudiantes WHERE id IN (SELECT id FROM _est_del);
DELETE FROM ades_personas    WHERE id IN (SELECT id FROM _per_del);
DELETE FROM ades_grupos      WHERE id IN (SELECT grupo_id FROM _grp2del);

SET LOCAL session_replication_role = DEFAULT;   -- reactivar FK + audit triggers

-- ============ 3. Crear grupos de 26A-2º (Metepec) ============
INSERT INTO ades_grupos (nombre_grupo, grado_id, ciclo_escolar_id, is_active)
SELECT v.ng,
       (SELECT g.id FROM ades_grados g JOIN ades_planteles p ON p.id=g.plantel_id
        JOIN ades_niveles_educativos n ON n.id=g.nivel_educativo_id
        WHERE n.nombre_nivel='PREPARATORIA' AND p.nombre_plantel='Metepec' AND g.numero_grado=2),
       (SELECT c.id FROM ades_ciclos_escolares c JOIN ades_niveles_educativos n ON n.id=c.nivel_educativo_id
        WHERE n.nombre_nivel='PREPARATORIA' AND c.nombre_ciclo='26A'),
       TRUE
FROM (VALUES ('A'), ('B')) AS v(ng);

-- ============ 4. Historial de la cohorte: 1º (25B) y 2º (26A), is_active=false ============
-- (uq_ades_inscripciones_activa_por_estudiante: un alumno tiene UNA sola activa = 26B-3º).
INSERT INTO ades_inscripciones (estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, is_active)
SELECT co.estudiante_id, gr.id, gr.ciclo_escolar_id, DATE '2025-08-04', FALSE
FROM _cohorte co
JOIN ades_grupos gr ON gr.nombre_grupo = co.nombre_grupo
JOIN ades_grados g ON g.id = gr.grado_id
JOIN ades_planteles p ON p.id = g.plantel_id
JOIN ades_ciclos_escolares c ON c.id = gr.ciclo_escolar_id
JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
WHERE n.nombre_nivel='PREPARATORIA' AND p.nombre_plantel='Metepec'
  AND c.nombre_ciclo='25B' AND g.numero_grado=1;

INSERT INTO ades_inscripciones (estudiante_id, grupo_id, ciclo_escolar_id, fecha_inscripcion, is_active)
SELECT co.estudiante_id, gr.id, gr.ciclo_escolar_id, DATE '2026-02-01', FALSE
FROM _cohorte co
JOIN ades_grupos gr ON gr.nombre_grupo = co.nombre_grupo
JOIN ades_grados g ON g.id = gr.grado_id
JOIN ades_planteles p ON p.id = g.plantel_id
JOIN ades_ciclos_escolares c ON c.id = gr.ciclo_escolar_id
JOIN ades_niveles_educativos n ON n.id = c.nivel_educativo_id
WHERE n.nombre_nivel='PREPARATORIA' AND p.nombre_plantel='Metepec'
  AND c.nombre_ciclo='26A' AND g.numero_grado=2;

COMMIT;
