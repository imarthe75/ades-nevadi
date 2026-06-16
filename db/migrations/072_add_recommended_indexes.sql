-- =============================================================================
-- SPRINT 3 FASE 2: Crear Índices Recomendados (FK + Compuestos)
-- =============================================================================
-- Crear 25+ índices basados en análisis de SPRINT 2:
-- - 20+ índices para Foreign Keys sin índice
-- - 5 índices compuestos para queries frecuentes
-- 
-- Impacto esperado: +30-40% en SELECT/JOIN, +40% en reportes

\echo '=== CREANDO ÍNDICES EN FOREIGN KEYS (20+) ==='

-- FK sin índice en ades_acuerdos_convivencia
CREATE INDEX IF NOT EXISTS idx_ades_acuerdos_convivencia_alumno_id 
  ON ades_acuerdos_convivencia(alumno_id);
\echo '✓ idx_ades_acuerdos_convivencia_alumno_id'

-- FK sin índice en ades_avance_planificacion
CREATE INDEX IF NOT EXISTS idx_ades_avance_planificacion_planeacion_clase_id 
  ON ades_avance_planificacion(planeacion_clase_id);
\echo '✓ idx_ades_avance_planificacion_planeacion_clase_id'

-- FK sin índice en ades_bajas
CREATE INDEX IF NOT EXISTS idx_ades_bajas_autorizado_por_id 
  ON ades_bajas(autorizado_por_id);
\echo '✓ idx_ades_bajas_autorizado_por_id'

-- FK sin índice en ades_bajas
CREATE INDEX IF NOT EXISTS idx_ades_bajas_inscripcion_id 
  ON ades_bajas(inscripcion_id);
\echo '✓ idx_ades_bajas_inscripcion_id'

-- FK sin índice en ades_calificaciones_tareas
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_tareas_estudiante_id 
  ON ades_calificaciones_tareas(estudiante_id);
\echo '✓ idx_ades_calificaciones_tareas_estudiante_id'

-- FK sin índice en ades_calificaciones_tareas
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_tareas_tarea_id 
  ON ades_calificaciones_tareas(tarea_id);
\echo '✓ idx_ades_calificaciones_tareas_tarea_id'

-- FK sin índice en ades_cambios_grupo
CREATE INDEX IF NOT EXISTS idx_ades_cambios_grupo_inscripcion_id 
  ON ades_cambios_grupo(inscripcion_id);
\echo '✓ idx_ades_cambios_grupo_inscripcion_id'

-- FK sin índice en ades_cambios_grupo
CREATE INDEX IF NOT EXISTS idx_ades_cambios_grupo_grupo_destino_id 
  ON ades_cambios_grupo(grupo_destino_id);
\echo '✓ idx_ades_cambios_grupo_grupo_destino_id'

-- FK sin índice en ades_certificados
CREATE INDEX IF NOT EXISTS idx_ades_certificados_estudiante_id 
  ON ades_certificados(estudiante_id);
\echo '✓ idx_ades_certificados_estudiante_id'

-- FK sin índice en ades_cierre_periodo_log
CREATE INDEX IF NOT EXISTS idx_ades_cierre_periodo_log_grupo_id 
  ON ades_cierre_periodo_log(grupo_id);
\echo '✓ idx_ades_cierre_periodo_log_grupo_id'

-- FK sin índice en ades_comunicados
CREATE INDEX IF NOT EXISTS idx_ades_comunicados_plantel_id 
  ON ades_comunicados(plantel_id);
\echo '✓ idx_ades_comunicados_plantel_id'

-- FK sin índice en ades_cuotas_pagos
CREATE INDEX IF NOT EXISTS idx_ades_cuotas_pagos_estudiante_id 
  ON ades_cuotas_pagos(estudiante_id);
\echo '✓ idx_ades_cuotas_pagos_estudiante_id'

-- FK sin índice en ades_documentos_almacenados
CREATE INDEX IF NOT EXISTS idx_ades_documentos_almacenados_estudiante_id 
  ON ades_documentos_almacenados(estudiante_id);
\echo '✓ idx_ades_documentos_almacenados_estudiante_id'

-- FK sin índice en ades_encuesta_respuestas
CREATE INDEX IF NOT EXISTS idx_ades_encuesta_respuestas_encuesta_id 
  ON ades_encuesta_respuestas(encuesta_id);
\echo '✓ idx_ades_encuesta_respuestas_encuesta_id'

-- FK sin índice en ades_encuesta_respuestas
CREATE INDEX IF NOT EXISTS idx_ades_encuesta_respuestas_usuario_id 
  ON ades_encuesta_respuestas(usuario_id);
\echo '✓ idx_ades_encuesta_respuestas_usuario_id'

-- FK sin índice en ades_evaluaciones
CREATE INDEX IF NOT EXISTS idx_ades_evaluaciones_clase_id 
  ON ades_evaluaciones(clase_id);
\echo '✓ idx_ades_evaluaciones_clase_id'

-- FK sin índice en ades_expedientes_medicos
CREATE INDEX IF NOT EXISTS idx_ades_expedientes_medicos_estudiante_id 
  ON ades_expedientes_medicos(estudiante_id);
\echo '✓ idx_ades_expedientes_medicos_estudiante_id'

-- FK sin índice en ades_historico_identidad
CREATE INDEX IF NOT EXISTS idx_ades_historico_identidad_persona_id 
  ON ades_historico_identidad(persona_id);
\echo '✓ idx_ades_historico_identidad_persona_id'

-- FK sin índice en ades_items_ponderacion
CREATE INDEX IF NOT EXISTS idx_ades_items_ponderacion_esquema_id 
  ON ades_items_ponderacion(esquema_id);
\echo '✓ idx_ades_items_ponderacion_esquema_id'

\echo ''
\echo '=== CREANDO ÍNDICES COMPUESTOS (5) ==='

-- Índice compuesto para queries frecuentes de asistencia
CREATE INDEX IF NOT EXISTS idx_ades_asistencias_estudiante_clase_fecha 
  ON ades_asistencias(estudiante_id, clase_id, fecha);
\echo '✓ idx_ades_asistencias_estudiante_clase_fecha'

-- Índice compuesto para queries de calificaciones por período
CREATE INDEX IF NOT EXISTS idx_ades_calificaciones_periodo_estudiante_periodo_acreditado
  ON ades_calificaciones_periodo(estudiante_id, periodo_id, es_acreditado);
\echo '✓ idx_ades_calificaciones_periodo_estudiante_periodo_acreditado'

-- Índice compuesto para búsquedas de personas por CURP/RFC
CREATE INDEX IF NOT EXISTS idx_ades_personas_curp_rfc
  ON ades_personas(curp, rfc) WHERE curp IS NOT NULL;
\echo '✓ idx_ades_personas_curp_rfc'

-- Índice compuesto para inscripciones activas
CREATE INDEX IF NOT EXISTS idx_ades_inscripciones_estudiante_grupo_activa
  ON ades_inscripciones(estudiante_id, grupo_id) WHERE is_active = TRUE;
\echo '✓ idx_ades_inscripciones_estudiante_grupo_activa'

-- Índice compuesto para búsquedas en alertas académicas
CREATE INDEX IF NOT EXISTS idx_ades_alertas_academicas_estudiante_estado
  ON ades_alertas_academicas(estudiante_id, estado) WHERE estado = 'ACTIVA';
\echo '✓ idx_ades_alertas_academicas_estudiante_estado'

\echo ''
\echo '=== VERIFICACIÓN: ÍNDICES CREADOS EXITOSAMENTE ==='
SELECT COUNT(*) as new_indexes FROM pg_indexes WHERE schemaname = 'public';

