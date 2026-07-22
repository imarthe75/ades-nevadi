-- 170_limpieza_alumnos_sin_inscripcion.sql
-- Elimina el ruido demo pre-existente: alumnos ACTIVOS que nunca tuvieron inscripción
-- (645 al 2026-07-22: Metepec 385 + Tenancingo 260). Son artefactos del generador de
-- simulación (creó más ades_estudiantes de los que matriculó); un alumno real siempre
-- está inscrito. Autorizado por el usuario ("limpia el ruido preexistente"; datos de prueba).
--
-- Borrado FÍSICO por session_replication_role=replica + loops dinámicos sobre las FK de
-- estudiante/persona/usuario (mismo método que la mig 167). Al no tener inscripciones,
-- su cascada es ligera (persona, usuario y expediente/contactos), pero se limpia todo el
-- árbol de forma robusta. Ver memoria borrado-fisico-alumno-replica.

BEGIN;

CREATE TEMP TABLE _est_del ON COMMIT DROP AS
SELECT e.id
FROM ades_estudiantes e
WHERE e.is_active
  AND NOT EXISTS (SELECT 1 FROM ades_inscripciones i WHERE i.estudiante_id = e.id);

CREATE TEMP TABLE _per_del ON COMMIT DROP AS
SELECT persona_id AS id FROM ades_estudiantes WHERE id IN (SELECT id FROM _est_del);

CREATE TEMP TABLE _usr_del ON COMMIT DROP AS
SELECT id FROM ades_usuarios WHERE persona_id IN (SELECT id FROM _per_del);

SET LOCAL session_replication_role = replica;

-- nieto conocido (por si algún huérfano tuviera entregas sueltas)
DELETE FROM ades_calificaciones_tareas WHERE tarea_entrega_id IN
  (SELECT id FROM ades_tareas_entregas WHERE estudiante_id IN (SELECT id FROM _est_del));

-- hijos directos de estudiantes / usuarios / personas
DO $$
DECLARE r RECORD; raiz TEXT; tgt TEXT;
BEGIN
  FOR raiz, tgt IN SELECT * FROM (VALUES
      ('ades_estudiantes','_est_del'), ('ades_usuarios','_usr_del'), ('ades_personas','_per_del')
  ) v(raiz, tgt) LOOP
    FOR r IN SELECT DISTINCT c.conrelid::regclass AS child, att.attname AS col
             FROM pg_constraint c JOIN unnest(c.conkey) AS k(attnum) ON true
             JOIN pg_attribute att ON att.attrelid=c.conrelid AND att.attnum=k.attnum
             WHERE c.confrelid = raiz::regclass AND c.contype='f'
    LOOP
      EXECUTE format('DELETE FROM %s WHERE %I IN (SELECT id FROM %I)', r.child, r.col, tgt);
    END LOOP;
  END LOOP;
END $$;

-- raíces
DELETE FROM ades_usuarios    WHERE id IN (SELECT id FROM _usr_del);
DELETE FROM ades_estudiantes WHERE id IN (SELECT id FROM _est_del);
DELETE FROM ades_personas    WHERE id IN (SELECT id FROM _per_del);

COMMIT;
