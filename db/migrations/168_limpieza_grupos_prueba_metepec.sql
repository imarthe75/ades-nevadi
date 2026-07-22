-- 168_limpieza_grupos_prueba_metepec.sql
-- Elimina 11 grupos basura 'TestGrpNNN' que quedaron en Metepec Primaria 1er grado
-- (ciclo vigente 2026-2027) tras corridas de pruebas E2E/fuzz sin limpiar — violación
-- de la Regla Mandatoria #28 detectada por el usuario navegando el dashboard (inflaban
-- el conteo de grupos de primaria de 12 a 23). Verificado 2026-07-22: los 11 están
-- COMPLETAMENTE vacíos (0 inscripciones, 0 horarios, 0 clases, 0 asignaciones, 0 foros),
-- así que el borrado es directo. El guard NOT IN(inscripciones) evita tocar cualquier
-- grupo 'TestGrp*' que por accidente tuviera alumnos reales.

BEGIN;

DELETE FROM ades_grupos
WHERE nombre_grupo LIKE 'TestGrp%'
  AND id NOT IN (SELECT grupo_id FROM ades_inscripciones);

COMMIT;
