-- 166_ajuste_historia_prepa_25b.sql
-- Ajusta la HISTORIA (ciclo cerrado 25B) de Preparatoria a la realidad declarada
-- (continuación de la mig 164, que corrigió el vigente 26B):
--   • Metepec  → plantel joven: en 25B solo cursó 1º y 2º semestre (su cohorte ya
--                avanzó a 3º en 26B). Se eliminan sus semestres 3º-6º (irreales).
--   • Tenancingo → plantel NUEVO que abre en 26B: NO tiene historia. Se elimina por
--                completo del ciclo 25B (los 6 semestres demo).
-- Confirmado por el usuario (2026-07-22): "déjalo real, la parte de preparatoria".
--
-- Datos de prueba: autorizado eliminar información dependiente. El 25B tiene horarios,
-- asignaciones docentes, foros, etc. colgando de esos grupos. Como las ~30 tablas hijas
-- de ades_grupos tienen FK NO ACTION (no CASCADE), se borran TODAS dinámicamente
-- (recorriendo pg_constraint por la columna FK real de cada una) ANTES de los grupos.
-- Transacción con ON_ERROR_STOP: si un hijo tuviera a su vez nietos que lo bloqueen,
-- aborta sin pérdida.

BEGIN;

-- Grupos de prepa 25B que se ELIMINAN: Metepec semestres >2, y todo Tenancingo.
CREATE TEMP TABLE _del25b ON COMMIT DROP AS
SELECT gr.id AS grupo_id
FROM ades_grupos gr
JOIN ades_grados g ON g.id = gr.grado_id
JOIN ades_planteles p ON p.id = g.plantel_id
JOIN ades_ciclos_escolares c ON c.id = gr.ciclo_escolar_id
JOIN ades_niveles_educativos ne ON ne.id = c.nivel_educativo_id
WHERE ne.nombre_nivel = 'PREPARATORIA' AND c.nombre_ciclo = '25B'
  AND ( (p.nombre_plantel = 'Metepec' AND g.numero_grado > 2)
     OR  p.nombre_plantel = 'Tenancingo' );

-- Subárbol de foros: ades_foros (hijo de grupos) → ades_mensajes_foro → ades_respuestas_foro.
-- Se borra de hoja a raíz: respuestas, luego mensajes (los foros caen en el loop de abajo).
DELETE FROM ades_respuestas_foro
WHERE mensaje_id IN (
    SELECT m.id FROM ades_mensajes_foro m
    WHERE m.foro_id IN (SELECT id FROM ades_foros WHERE grupo_id IN (SELECT grupo_id FROM _del25b)));

DELETE FROM ades_mensajes_foro
WHERE foro_id IN (SELECT id FROM ades_foros WHERE grupo_id IN (SELECT grupo_id FROM _del25b));

-- Borra dinámicamente cada fila hija que referencie a esos grupos, en cualquier tabla
-- con FK a ades_grupos, usando la columna FK real de cada constraint.
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN
    SELECT DISTINCT c.conrelid::regclass AS child, att.attname AS col
    FROM pg_constraint c
    JOIN unnest(c.conkey) AS k(attnum) ON true
    JOIN pg_attribute att ON att.attrelid = c.conrelid AND att.attnum = k.attnum
    WHERE c.confrelid = 'ades_grupos'::regclass AND c.contype = 'f'
  LOOP
    EXECUTE format('DELETE FROM %s WHERE %I IN (SELECT grupo_id FROM _del25b)', r.child, r.col);
  END LOOP;
END $$;

-- Finalmente, los grupos.
DELETE FROM ades_grupos WHERE id IN (SELECT grupo_id FROM _del25b);

COMMIT;
