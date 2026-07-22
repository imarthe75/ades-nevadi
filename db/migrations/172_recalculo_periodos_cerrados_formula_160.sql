-- 172_recalculo_periodos_cerrados_formula_160.sql
-- OPCIONAL / NO EJECUTAR SIN LEER. Recalcula los períodos CERRADOS con la fórmula nueva
-- (mig 160, promedio real de calificacion_obtenida). La mig 160 solo recalculó los ~48
-- períodos ABIERTOS; los cerrados conservan la fórmula vieja (tasa de cumplimiento).
--
-- ⚠️ ALCANCE REAL: 35,100 períodos cerrados (34,777 divergentes). La función
-- calcular_calificacion_periodo PROTEGE deliberadamente la nota de los períodos cerrados
-- (`WHEN cerrada = TRUE THEN calificacion_final`), porque en un sistema real una nota
-- finalizada NO se cambia retroactivamente. Esta migración SOBRESCRIBE esa protección
-- para 35 mil registros DEMO. Es una operación pesada (35k ejecuciones de la función).
--
-- RECOMENDACIÓN (Agente Residente): NO correr en general. Es data de prueba que se
-- regenera; la guía QA ya indica "usar datos NUEVOS" para CU-6, lo que evita el problema
-- sin tocar la protección de notas cerradas. Correr SOLO si se decide explícitamente
-- dejar el histórico demo consistente con la fórmula nueva.

BEGIN;

-- 1) Recalcular calificacion_calculada de cada período cerrado con la fórmula nueva.
DO $$
DECLARE r RECORD;
BEGIN
  FOR r IN
    SELECT DISTINCT estudiante_id, grupo_id, materia_id, periodo_evaluacion_id
    FROM ades_calificaciones_periodo
    WHERE cerrada = TRUE
  LOOP
    PERFORM calcular_calificacion_periodo(
      r.estudiante_id, r.grupo_id, r.materia_id, r.periodo_evaluacion_id);
  END LOOP;
END $$;

-- 2) Aplicar el nuevo valor a calificacion_final (la función lo protegió por estar cerrado).
UPDATE ades_calificaciones_periodo
SET calificacion_final = calificacion_calculada
WHERE cerrada = TRUE
  AND calificacion_final IS DISTINCT FROM calificacion_calculada;

COMMIT;
