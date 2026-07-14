-- =============================================================================
-- 134_fix_minimo_aprobatorio_preparatoria.sql
-- La migración 091 corrigió escala_maxima de PREPARATORIA de 100.0 a 10.0
-- (RGEMS UAEMEX es 0-10, igual que primaria/secundaria) pero dejó
-- minimo_aprobatorio sin corregir — quedó en 60.0, residuo de la escala vieja
-- de 100 puntos. Consecuencia real verificada: GradebookQueryService compara
-- `cp.calificacion_final >= ne.minimo_aprobatorio` para acreditado/en_riesgo;
-- con escala_maxima=10 y minimo_aprobatorio=60, ningún alumno de Preparatoria
-- puede acreditar jamás (10 nunca es >= 60) — todos aparecen "en riesgo".
-- Detectado durante análisis de una auditoría externa (2026-07-14) que
-- afirmaba erróneamente que UAEMEX usa escala 0-100 (falso, ver migración 091);
-- la investigación de esa afirmación reveló este bug real y distinto.
-- =============================================================================

UPDATE ades_niveles_educativos
SET minimo_aprobatorio = 6.0
WHERE nombre_nivel = 'PREPARATORIA' AND escala_maxima = 10.0;
