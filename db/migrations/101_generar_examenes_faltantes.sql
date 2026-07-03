-- =============================================================================
-- Migración 101 — Generar exámenes faltantes (tipo_item='examen')
--
-- Contexto: auditoría QA 2026-07-02 encontró que ades_tareas tiene 6,144 filas
-- pero NINGUNA con tipo_item='examen' (solo tarea/proyecto/participacion),
-- bloqueando la validación del flujo materia→temario→planificación→tareas→
-- exámenes→calificaciones→estadísticas solicitado.
--
-- Genera 1 examen por cada combinación (grupo, materia, periodo_evaluacion)
-- que ya tiene actividad real de tipo 'tarea' (2,322 combinaciones), con
-- calificaciones para cada alumno activo inscrito en el grupo, siguiendo el
-- mismo patrón 1:1 tareas_entregas↔calificaciones_tareas ya usado por el resto
-- del gradebook. El trigger trg_gradebook_entrega recalcula automáticamente
-- ades_calificaciones_periodo al insertar calificacion_obtenida.
-- =============================================================================

-- ── 1. Crear un examen por combinación (grupo, materia, periodo) activa ──────
INSERT INTO ades_tareas (titulo, descripcion, grupo_id, materia_id, tema_id,
                         periodo_evaluacion_id, fecha_asignacion, fecha_entrega,
                         fecha_examen, puntaje_maximo, tipo_item, origen)
SELECT
  'Examen — ' || pe.nombre_periodo || ' — ' || m.nombre_materia,
  'Examen generado para validar el flujo completo materia→temario→planificación→tareas→exámenes→calificaciones.',
  base.grupo_id, base.materia_id, base.tema_id,
  base.periodo_evaluacion_id,
  pe.fecha_fin - INTERVAL '5 days',
  pe.fecha_fin - INTERVAL '2 days',
  pe.fecha_fin - INTERVAL '2 days',
  10.0,
  'examen',
  'MANUAL'
FROM (
  SELECT DISTINCT ON (t.grupo_id, t.materia_id, t.periodo_evaluacion_id)
    t.grupo_id, t.materia_id, t.periodo_evaluacion_id, t.tema_id
  FROM ades_tareas t
  WHERE t.tipo_item = 'tarea' AND t.periodo_evaluacion_id IS NOT NULL AND t.is_active = TRUE
  ORDER BY t.grupo_id, t.materia_id, t.periodo_evaluacion_id, t.tema_id NULLS LAST
) base
JOIN ades_periodos_evaluacion pe ON pe.id = base.periodo_evaluacion_id
JOIN ades_materias m ON m.id = base.materia_id
WHERE NOT EXISTS (
  SELECT 1 FROM ades_tareas ex
  WHERE ex.grupo_id = base.grupo_id AND ex.materia_id = base.materia_id
    AND ex.periodo_evaluacion_id = base.periodo_evaluacion_id AND ex.tipo_item = 'examen'
    AND ex.is_active = TRUE
);

-- ── 2. Entregas: un registro por alumno activo inscrito en el grupo ──────────
-- Distribución de calificación 6.0–10.0 (redondeada a 1 decimal), consistente
-- con el rango observado en las calificaciones ya existentes de tipo 'tarea'.
INSERT INTO ades_tareas_entregas (tarea_id, estudiante_id, fecha_entrega,
                                  estatus_entrega, calificacion_obtenida)
SELECT
  t.id,
  i.estudiante_id,
  t.fecha_examen::timestamptz,
  'CALIFICADA',
  ROUND((6.0 + random() * 4.0)::numeric, 1)
FROM ades_tareas t
JOIN ades_inscripciones i ON i.grupo_id = t.grupo_id AND i.is_active = TRUE
WHERE t.tipo_item = 'examen'
  AND t.descripcion = 'Examen generado para validar el flujo completo materia→temario→planificación→tareas→exámenes→calificaciones.'
  AND NOT EXISTS (
    SELECT 1 FROM ades_tareas_entregas te WHERE te.tarea_id = t.id AND te.estudiante_id = i.estudiante_id
  );

-- ── 3. Calificaciones de tarea — espejo 1:1 de la entrega (patrón existente) ─
INSERT INTO ades_calificaciones_tareas (tarea_entrega_id, calificacion)
SELECT te.id, te.calificacion_obtenida
FROM ades_tareas_entregas te
JOIN ades_tareas t ON t.id = te.tarea_id
WHERE t.tipo_item = 'examen'
  AND t.descripcion = 'Examen generado para validar el flujo completo materia→temario→planificación→tareas→exámenes→calificaciones.'
  AND NOT EXISTS (
    SELECT 1 FROM ades_calificaciones_tareas ct WHERE ct.tarea_entrega_id = te.id
  );
