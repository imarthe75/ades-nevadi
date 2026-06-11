-- =============================================================================
-- MIGRACIÓN 028 — FASE 4B: Learning Paths — Recursos + columna IA
-- Fecha: 2026-06-10
-- Propósito:
--   1. Agrega columna ia_recomendacion JSONB a ades_lp_asignaciones
--   2. Agrega columna ia_analisis JSONB a ades_alertas_academicas
--   3. Puebla ades_lp_recursos con contenido real para los 4 paths
--   4. Agrega trigger de auditoría a ades_lp_recursos y ades_lp_asignaciones
-- =============================================================================

-- ── 1. Columna IA en asignaciones ─────────────────────────────────────────────
ALTER TABLE ades_lp_asignaciones
  ADD COLUMN IF NOT EXISTS ia_recomendacion JSONB DEFAULT NULL;

COMMENT ON COLUMN ades_lp_asignaciones.ia_recomendacion
  IS 'JSON con análisis Claude: {resumen, fortalezas, areas_mejora, estrategias, recursos_priorizados}';

-- ── 2. Columna IA en alertas ────────────────────────────────────────────────
ALTER TABLE ades_alertas_academicas
  ADD COLUMN IF NOT EXISTS ia_analisis JSONB DEFAULT NULL;

COMMENT ON COLUMN ades_alertas_academicas.ia_analisis
  IS 'Análisis IA de la alerta: contexto académico, patrones y recomendaciones generadas por Claude';

-- ── 3. Columnas de auditoría faltantes + triggers ────────────────────────────
-- ades_lp_recursos y ades_lp_asignaciones no tenían usuario_creacion/modificacion ni ref
ALTER TABLE ades_lp_recursos
  ADD COLUMN IF NOT EXISTS ref                  UUID DEFAULT gen_random_uuid(),
  ADD COLUMN IF NOT EXISTS usuario_creacion     TEXT DEFAULT CURRENT_USER,
  ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT DEFAULT CURRENT_USER;

ALTER TABLE ades_lp_asignaciones
  ADD COLUMN IF NOT EXISTS ref                  UUID DEFAULT gen_random_uuid(),
  ADD COLUMN IF NOT EXISTS is_active            BOOLEAN DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS usuario_creacion     TEXT DEFAULT CURRENT_USER,
  ADD COLUMN IF NOT EXISTS usuario_modificacion TEXT DEFAULT CURRENT_USER;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname='trg_lp_recursos_biu'
  ) THEN
    CREATE TRIGGER trg_lp_recursos_biu
      BEFORE INSERT OR UPDATE ON ades_lp_recursos
      FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname='trg_lp_asignaciones_biu'
  ) THEN
    CREATE TRIGGER trg_lp_asignaciones_biu
      BEFORE INSERT OR UPDATE ON ades_lp_asignaciones
      FOR EACH ROW EXECUTE FUNCTION auditoria.fn_auditoria_biu();
  END IF;
END $$;

-- ── 4. Recursos para: Refuerzo de Comprensión Lectora ───────────────────────
INSERT INTO ades_lp_recursos (id, path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active)
SELECT uuidv7(), lp.id, r.orden, r.tipo, r.titulo, r.desc_, r.url, r.dur, r.oblig, TRUE
FROM ades_learning_paths lp, (VALUES
  (1, 'VIDEO',     'Estrategias de lectura activa',
   'Técnicas de subrayado, mapas mentales y lectura por párrafos para comprensión profunda.',
   'https://www.youtube.com/watch?v=comprension-lectora-sep', 15, TRUE),
  (2, 'PDF',       'Guía de lectura inferencial SEP',
   'Material oficial SEP: cómo deducir significado de palabras en contexto y responder preguntas de comprensión.',
   NULL, 30, TRUE),
  (3, 'EJERCICIO', 'Práctica: Textos informativos nivel básico',
   'Serie de 10 textos breves con preguntas de comprensión literal e inferencial, auto-calificables.',
   NULL, 45, TRUE),
  (4, 'VIDEO',     'Cómo identificar la idea principal',
   'Método paso a paso para separar idea principal de ideas secundarias en textos narrativos y expositivos.',
   'https://www.youtube.com/watch?v=idea-principal-texto', 12, FALSE),
  (5, 'EJERCICIO', 'Simulacro PLANEA — Comprensión lectora',
   'Reactivos tipo PLANEA para autoevaluación y práctica dirigida.',
   NULL, 60, FALSE)
) AS r(orden, tipo, titulo, desc_, url, dur, oblig)
WHERE lp.nombre = 'Refuerzo de Comprensión Lectora'
  AND NOT EXISTS (SELECT 1 FROM ades_lp_recursos WHERE path_id = lp.id);

-- ── 5. Recursos para: Nivelación Matemática Básica ──────────────────────────
INSERT INTO ades_lp_recursos (id, path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active)
SELECT uuidv7(), lp.id, r.orden, r.tipo, r.titulo, r.desc_, r.url, r.dur, r.oblig, TRUE
FROM ades_learning_paths lp, (VALUES
  (1, 'VIDEO',     'Repaso: Operaciones básicas con números naturales',
   'Video-clase de suma, resta, multiplicación y división con ejemplos paso a paso.',
   'https://www.khanacademy.org/math/arithmetic', 20, TRUE),
  (2, 'EJERCICIO', 'Diagnóstico: Tabla de multiplicar y división',
   'Ejercicio de diagnóstico rápido (5 min) para identificar brechas en operaciones aritméticas.',
   NULL, 10, TRUE),
  (3, 'VIDEO',     'Fracciones: de cero hasta resolución de problemas',
   'Serie de 3 videos sobre fracciones equivalentes, suma/resta y multiplicación de fracciones.',
   'https://www.khanacademy.org/math/arithmetic/fraction-arithmetic', 35, TRUE),
  (4, 'PDF',       'Fichas de práctica: Resolución de problemas',
   '20 problemas de aplicación con distintas operaciones, organizados por nivel de dificultad.',
   NULL, 40, TRUE),
  (5, 'EJERCICIO', 'Simulacro PLANEA — Matemáticas nivel básico',
   'Reactivos tipo PLANEA enfocados en aritmética, fracciones y proporciones.',
   NULL, 50, FALSE),
  (6, 'VIDEO',     'Introducción a álgebra básica',
   'Variables, expresiones algebraicas simples y ecuaciones de primer grado.',
   'https://www.khanacademy.org/math/algebra-basics', 25, FALSE)
) AS r(orden, tipo, titulo, desc_, url, dur, oblig)
WHERE lp.nombre = 'Nivelación Matemática Básica'
  AND NOT EXISTS (SELECT 1 FROM ades_lp_recursos WHERE path_id = lp.id);

-- ── 6. Recursos para: Plan de Asistencia y Hábitos ──────────────────────────
INSERT INTO ades_lp_recursos (id, path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active)
SELECT uuidv7(), lp.id, r.orden, r.tipo, r.titulo, r.desc_, r.url, r.dur, r.oblig, TRUE
FROM ades_learning_paths lp, (VALUES
  (1, 'VIDEO',     'Por qué asistir a la escuela transforma tu futuro',
   'Video motivacional con datos sobre el impacto del ausentismo en el aprendizaje y las oportunidades.',
   NULL, 8, TRUE),
  (2, 'PDF',       'Guía de organización y rutinas de estudio',
   'Material práctico: cómo construir una rutina diaria, manejo del tiempo y planificador semanal.',
   NULL, 20, TRUE),
  (3, 'EJERCICIO', 'Contrato de asistencia y metas personales',
   'Formato de compromiso con metas de asistencia y estrategias personalizadas para alcanzarlas.',
   NULL, 15, TRUE),
  (4, 'ENLACE',    'Recursos SEP: Orientación y tutoría escolar',
   'Portal SEP con recursos de orientación para alumnos con problemas de asistencia.',
   'https://www.gob.mx/sep/acciones-y-programas/tutoria-y-orientacion-escolar', 0, FALSE),
  (5, 'VIDEO',     'Gestión emocional para estudiantes',
   'Técnicas básicas de manejo de estrés, ansiedad escolar y motivación intrínseca.',
   NULL, 18, FALSE)
) AS r(orden, tipo, titulo, desc_, url, dur, oblig)
WHERE lp.nombre = 'Plan de Asistencia y Hábitos'
  AND NOT EXISTS (SELECT 1 FROM ades_lp_recursos WHERE path_id = lp.id);

-- ── 7. Recursos para: Acompañamiento Riesgo Alto ────────────────────────────
INSERT INTO ades_lp_recursos (id, path_id, orden, tipo, titulo, descripcion, url_recurso, duracion_min, obligatorio, is_active)
SELECT uuidv7(), lp.id, r.orden, r.tipo, r.titulo, r.desc_, r.url, r.dur, r.oblig, TRUE
FROM ades_learning_paths lp, (VALUES
  (1, 'EJERCICIO', 'Diagnóstico integral multi-materia',
   'Evaluación diagnóstica en las 4 materias principales para mapear brechas específicas.',
   NULL, 60, TRUE),
  (2, 'VIDEO',     'Técnicas de estudio efectivas: método Cornell y mapas conceptuales',
   'Dos métodos probados para tomar apuntes y retener información a largo plazo.',
   NULL, 22, TRUE),
  (3, 'PDF',       'Plan de recuperación académica personalizado',
   'Plantilla guiada para construir un plan de 8 semanas de recuperación con docente-tutor.',
   NULL, 30, TRUE),
  (4, 'VIDEO',     'Matemáticas: refuerzo de operaciones y resolución de problemas',
   'Repaso intensivo de los temas más frecuentemente reprobados en matemáticas.',
   'https://www.khanacademy.org/math', 40, TRUE),
  (5, 'VIDEO',     'Español: comprensión lectora y expresión escrita',
   'Talleres breves de lectura y escritura para elevar el desempeño en español.',
   NULL, 35, TRUE),
  (6, 'EJERCICIO', 'Seguimiento semanal: autoevaluación de progreso',
   'Checklist semanal de actividades completadas y autoreporte de dificultades al tutor.',
   NULL, 10, FALSE),
  (7, 'ENLACE',    'Recurso familia: cómo apoyar desde casa',
   'Guía para padres sobre cómo acompañar el proceso de recuperación en casa.',
   NULL, 0, FALSE)
) AS r(orden, tipo, titulo, desc_, url, dur, oblig)
WHERE lp.nombre = 'Acompañamiento Riesgo Alto'
  AND NOT EXISTS (SELECT 1 FROM ades_lp_recursos WHERE path_id = lp.id);

SELECT
  lp.nombre,
  COUNT(r.id) AS recursos,
  SUM(COALESCE(r.duracion_min, 0)) AS minutos_total
FROM ades_learning_paths lp
LEFT JOIN ades_lp_recursos r ON r.path_id = lp.id AND r.is_active = TRUE
GROUP BY lp.id, lp.nombre
ORDER BY lp.nombre;

SELECT 'Migration 028: recursos y columnas IA creados' AS status;
