-- =============================================================================
-- SPRINT 3 FASE 5A: Crear Materialized Views para Reportes
-- =============================================================================
-- Cache inteligente para queries de reportes sin sacrificar integridad ACID

\echo '=== CREANDO MATERIALIZED VIEWS PARA REPORTES ==='

-- 1. Vista materializada: Calificaciones y promedios por período
CREATE MATERIALIZED VIEW IF NOT EXISTS v_calificaciones_reportes AS
SELECT 
  cp.id,
  cp.estudiante_id,
  cp.periodo_id,
  cp.materia_id,
  cp.calificacion_final,
  CASE WHEN cp.calificacion_final >= 6.0 THEN true ELSE false END as es_acreditado,
  e.numero_control,
  e.nombre,
  m.nombre as materia_nombre,
  pe.nombre as periodo_nombre,
  cp.fecha_modificacion
FROM ades_calificaciones_periodo cp
JOIN ades_estudiantes e ON cp.estudiante_id = e.id
JOIN ades_materias m ON cp.materia_id = m.id
JOIN ades_periodos_evaluacion pe ON cp.periodo_id = pe.id
WHERE cp.estado = 'PUBLICADA';

CREATE UNIQUE INDEX idx_v_calificaciones_reportes_pk 
  ON v_calificaciones_reportes(id);

\echo '✓ v_calificaciones_reportes'

-- 2. Vista materializada: Asistencia por estudiante-período
CREATE MATERIALIZED VIEW IF NOT EXISTS v_asistencia_estadisticas AS
SELECT 
  a.estudiante_id,
  (SELECT nombre FROM ades_periodos_evaluacion WHERE id = c.periodo_id) as periodo,
  COUNT(*) as total_clases,
  SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END) as presentes,
  SUM(CASE WHEN a.estatus_asistencia = 'FALTA' THEN 1 ELSE 0 END) as faltas,
  SUM(CASE WHEN a.estatus_asistencia = 'TARDE' THEN 1 ELSE 0 END) as tardes,
  ROUND(100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END) / COUNT(*), 2) as porcentaje_asistencia
FROM ades_asistencias a
JOIN ades_clases c ON a.clase_id = c.id
GROUP BY a.estudiante_id, c.periodo_id;

\echo '✓ v_asistencia_estadisticas'

-- 3. Vista materializada: Estado académico por estudiante
CREATE MATERIALIZED VIEW IF NOT EXISTS v_estado_academico_estudiante AS
SELECT 
  e.id,
  e.numero_control,
  e.nombre,
  COUNT(DISTINCT cp.materia_id) as materias_inscritas,
  SUM(CASE WHEN cp.es_acreditado = true THEN 1 ELSE 0 END) as materias_acreditadas,
  ROUND(AVG(cp.calificacion_final), 2) as promedio_general,
  MAX(cp.calificacion_final) as calificacion_maxima,
  MIN(cp.calificacion_final) as calificacion_minima
FROM ades_estudiantes e
LEFT JOIN ades_calificaciones_periodo cp ON e.id = cp.estudiante_id
GROUP BY e.id, e.numero_control, e.nombre;

\echo '✓ v_estado_academico_estudiante'

\echo ''
\echo '=== VERIFICACIÓN: MATERIALIZED VIEWS CREADAS ==='
SELECT matviewname FROM pg_matviews WHERE schemaname = 'public';

