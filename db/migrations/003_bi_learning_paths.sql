-- =============================================================================
-- ADES — Migración 003: BI + Notificaciones (columna) + Learning Paths
-- Ejecutar: psql -U ades_admin -d ades -f 003_bi_learning_paths.sql
-- Versión:  2026-06-04
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. ades_notificaciones — agregar columna faltante si es necesario
--    La tabla ya existe con schema propio; solo nos aseguramos de índice clave.
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_notif_usuario_leido
    ON ades_notificaciones(usuario_id, leido);

-- ---------------------------------------------------------------------------
-- 2. Columna notificada en alertas académicas (si no existe)
-- ---------------------------------------------------------------------------
ALTER TABLE ades_alertas_academicas
    ADD COLUMN IF NOT EXISTS notificada BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------------
-- 3. Esquema BI + Vistas Materializadas (para Apache Superset)
--    Estructura real del schema:
--      ades_grados.plantel_id → ades_planteles.id
--      ades_grados.nivel_educativo_id → ades_niveles_educativos.id
--      ades_asistencias.estatus_asistencia  (no estatus)
--      ades_materias_plan.horas_semana      (no horas_semanales)
--      ades_clases.materia_id               (no materia_plan_id)
-- ---------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS ades_bi;
COMMENT ON SCHEMA ades_bi IS 'Vistas materializadas para BI — consumidas por Apache Superset';

-- 3.1 Asistencia diaria por grupo
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_asistencia_diaria;
CREATE MATERIALIZED VIEW ades_bi.mv_asistencia_diaria AS
SELECT
    cl.fecha_clase::date                                         AS fecha,
    cl.grupo_id,
    g.nombre_grupo,
    gr.nombre_grado,
    pl.nombre_plantel,
    ne.nombre_nivel,
    COUNT(DISTINCT a.estudiante_id)                             AS total_alumnos,
    SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE'  THEN 1 ELSE 0 END)  AS presentes,
    SUM(CASE WHEN a.estatus_asistencia = 'AUSENTE'   THEN 1 ELSE 0 END)  AS ausentes,
    SUM(CASE WHEN a.estatus_asistencia = 'TARDANZA'  THEN 1 ELSE 0 END)  AS tardanzas,
    ROUND(
        100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)
        / NULLIF(COUNT(DISTINCT a.estudiante_id), 0)
    , 2)                                                        AS pct_asistencia
FROM ades_asistencias a
JOIN ades_clases cl      ON cl.id = a.clase_id
JOIN ades_grupos g       ON g.id = cl.grupo_id
JOIN ades_grados gr      ON gr.id = g.grado_id
JOIN ades_planteles pl   ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
GROUP BY cl.grupo_id, a.fecha_clase::date, g.nombre_grupo, gr.nombre_grado,
         pl.nombre_plantel, ne.nombre_nivel
WITH DATA;

CREATE UNIQUE INDEX idx_mv_asistencia_diaria
    ON ades_bi.mv_asistencia_diaria (fecha, grupo_id);

-- 3.2 Calificaciones promedio por grupo y materia
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_calificaciones_grupo;
CREATE MATERIALIZED VIEW ades_bi.mv_calificaciones_grupo AS
SELECT
    i.grupo_id,
    g.nombre_grupo,
    gr.nombre_grado,
    pl.nombre_plantel,
    ne.nombre_nivel,
    m.nombre_materia,
    m.id                                                AS materia_id,
    pe.nombre_periodo,
    pe.numero_periodo,
    COUNT(DISTINCT cp.estudiante_id)                    AS alumnos_evaluados,
    ROUND(AVG(cp.calificacion_final)::numeric, 2)       AS promedio,
    MIN(cp.calificacion_final)                          AS minimo,
    MAX(cp.calificacion_final)                          AS maximo,
    SUM(CASE WHEN cp.calificacion_final >= 6.0 THEN 1 ELSE 0 END) AS aprobados,
    SUM(CASE WHEN cp.calificacion_final < 6.0  THEN 1 ELSE 0 END) AS reprobados
FROM ades_calificaciones_periodo cp
JOIN ades_inscripciones i  ON i.estudiante_id = cp.estudiante_id
                           AND i.grupo_id = cp.grupo_id AND i.is_active = TRUE
JOIN ades_grupos g         ON g.id = i.grupo_id
JOIN ades_grados gr        ON gr.id = g.grado_id
JOIN ades_planteles pl     ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_materias m       ON m.id = cp.materia_id
JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id
GROUP BY i.grupo_id, g.nombre_grupo, gr.nombre_grado, pl.nombre_plantel,
         ne.nombre_nivel, m.nombre_materia, m.id, pe.nombre_periodo, pe.numero_periodo
WITH DATA;

CREATE UNIQUE INDEX idx_mv_calificaciones_grupo
    ON ades_bi.mv_calificaciones_grupo (grupo_id, materia_id, numero_periodo);

-- 3.3 Riesgo académico por alumno (snapshot actual)
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_riesgo_academico;
CREATE MATERIALIZED VIEW ades_bi.mv_riesgo_academico AS
SELECT
    e.id                                                AS estudiante_id,
    p.nombre || ' ' || p.apellido_paterno               AS nombre_alumno,
    i.grupo_id,
    g.nombre_grupo,
    gr.nombre_grado,
    pl.nombre_plantel,
    ne.nombre_nivel,
    ROUND(AVG(cp.calificacion_final)::numeric, 2)       AS promedio_general,
    ROUND(
        100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)
        / NULLIF(COUNT(DISTINCT a.id), 0)
    , 1)                                                AS pct_asistencia,
    COUNT(DISTINCT CASE WHEN cp.calificacion_final < 6.0 THEN cp.materia_id END) AS materias_reprobadas,
    CASE
        WHEN AVG(cp.calificacion_final) < 5.0
          OR 100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)
             / NULLIF(COUNT(DISTINCT a.id), 0) < 70
        THEN 'ALTO'
        WHEN AVG(cp.calificacion_final) < 6.0
          OR 100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)
             / NULLIF(COUNT(DISTINCT a.id), 0) < 80
        THEN 'MEDIO'
        ELSE 'BAJO'
    END                                                 AS nivel_riesgo
FROM ades_estudiantes e
JOIN ades_personas p     ON p.id = e.persona_id
JOIN ades_inscripciones i ON i.estudiante_id = e.id AND i.is_active = TRUE
JOIN ades_ciclos_escolares c ON c.id = i.ciclo_escolar_id AND c.es_vigente = TRUE
JOIN ades_grupos g        ON g.id = i.grupo_id
JOIN ades_grados gr       ON gr.id = g.grado_id
JOIN ades_planteles pl    ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
LEFT JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = e.id AND cp.grupo_id = i.grupo_id
LEFT JOIN ades_clases cl  ON cl.grupo_id = i.grupo_id
LEFT JOIN ades_asistencias a ON a.clase_id = cl.id AND a.estudiante_id = e.id
GROUP BY e.id, p.nombre, p.apellido_paterno, i.grupo_id, g.nombre_grupo,
         gr.nombre_grado, pl.nombre_plantel, ne.nombre_nivel
WITH DATA;

CREATE UNIQUE INDEX idx_mv_riesgo_academico
    ON ades_bi.mv_riesgo_academico (estudiante_id, grupo_id);

-- 3.4 Resumen por plantel (KPIs ejecutivos)
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_resumen_plantel;
CREATE MATERIALIZED VIEW ades_bi.mv_resumen_plantel AS
SELECT
    pl.id                                               AS plantel_id,
    pl.nombre_plantel,
    ne.nombre_nivel,
    COUNT(DISTINCT i.estudiante_id)                     AS total_alumnos,
    COUNT(DISTINCT g.id)                                AS total_grupos,
    ROUND(AVG(cp.calificacion_final)::numeric, 2)       AS promedio_institucional,
    ROUND(
        100.0 * COUNT(DISTINCT CASE WHEN ra.nivel_riesgo = 'ALTO' THEN ra.estudiante_id END)
        / NULLIF(COUNT(DISTINCT i.estudiante_id), 0)
    , 1)                                                AS pct_riesgo_alto
FROM ades_planteles pl
JOIN ades_grados gr        ON gr.plantel_id = pl.id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_grupos g         ON g.grado_id = gr.id AND g.is_active = TRUE
JOIN ades_inscripciones i  ON i.grupo_id = g.id AND i.is_active = TRUE
JOIN ades_ciclos_escolares c ON c.id = i.ciclo_escolar_id AND c.es_vigente = TRUE
LEFT JOIN ades_calificaciones_periodo cp ON cp.estudiante_id = i.estudiante_id
LEFT JOIN ades_bi.mv_riesgo_academico ra ON ra.estudiante_id = i.estudiante_id
GROUP BY pl.id, pl.nombre_plantel, ne.nombre_nivel
WITH DATA;

CREATE UNIQUE INDEX idx_mv_resumen_plantel
    ON ades_bi.mv_resumen_plantel (plantel_id, nombre_nivel);

-- 3.5 Cobertura curricular (% temas cubiertos vs. plan de estudios)
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_cobertura_curricular;
CREATE MATERIALIZED VIEW ades_bi.mv_cobertura_curricular AS
SELECT
    mp.grado_id,
    gr.nombre_grado,
    pl.nombre_plantel,
    m.nombre_materia,
    mp.id                                               AS materia_plan_id,
    mp.horas_semana,
    COUNT(DISTINCT cl.id)                               AS clases_impartidas,
    ROUND(mp.horas_semana * 40, 0)                      AS horas_esperadas_ciclo,
    ROUND(
        100.0 * COUNT(DISTINCT cl.id)
        / NULLIF(ROUND(mp.horas_semana * 40, 0), 0)
    , 1)                                                AS pct_cobertura
FROM ades_materias_plan mp
JOIN ades_materias m    ON m.id = mp.materia_id
JOIN ades_grados gr     ON gr.id = mp.grado_id
JOIN ades_planteles pl  ON pl.id = gr.plantel_id
LEFT JOIN ades_clases cl ON cl.materia_id = mp.materia_id
                        AND cl.grupo_id IN (
                            SELECT id FROM ades_grupos WHERE grado_id = mp.grado_id AND is_active = TRUE
                        )
WHERE mp.is_active = TRUE
GROUP BY mp.grado_id, gr.nombre_grado, pl.nombre_plantel,
         m.nombre_materia, mp.id, mp.horas_semana
WITH DATA;

CREATE UNIQUE INDEX idx_mv_cobertura_curricular
    ON ades_bi.mv_cobertura_curricular (materia_plan_id);

-- Permisos para Superset (usuario de solo lectura)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'superset_ro') THEN
        CREATE ROLE superset_ro LOGIN PASSWORD 'superset_ro_changeme';
    END IF;
END$$;

GRANT USAGE ON SCHEMA ades_bi TO superset_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA ades_bi TO superset_ro;
GRANT USAGE ON SCHEMA public TO superset_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO superset_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA ades_bi GRANT SELECT ON TABLES TO superset_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public  GRANT SELECT ON TABLES TO superset_ro;

-- ---------------------------------------------------------------------------
-- 4. Learning Paths — rutas de refuerzo adaptativas
--    Las tablas ya fueron creadas en la primera ejecución parcial.
--    Solo añadimos seeds si la tabla está vacía.
-- ---------------------------------------------------------------------------
INSERT INTO ades_learning_paths (nombre, descripcion, criterio_activacion, umbral_activacion, is_active)
SELECT nombre, descripcion, criterio_activacion, umbral_activacion, is_active FROM (VALUES
    ('Refuerzo de Comprensión Lectora',
     'Actividades de refuerzo para alumnos con dificultades en comprensión de textos. Videos cortos, ejercicios graduados y autoevaluación.',
     'REPROBACION'::varchar, 6.0::numeric, TRUE),
    ('Nivelación Matemática Básica',
     'Ruta de refuerzo para operaciones aritméticas, fracciones y álgebra básica según nivel educativo.',
     'REPROBACION'::varchar, 6.0::numeric, TRUE),
    ('Plan de Asistencia y Hábitos',
     'Orientación para alumnos con ausentismo crítico: recursos de gestión emocional, hábitos de estudio y comunicación con padres.',
     'AUSENTISMO'::varchar, 80.0::numeric, TRUE),
    ('Acompañamiento Riesgo Alto',
     'Ruta integral para alumnos en riesgo académico alto: diagnostico, refuerzo multi-materia y seguimiento semanal.',
     'RIESGO_ALTO'::varchar, NULL, TRUE)
) AS v(nombre, descripcion, criterio_activacion, umbral_activacion, is_active)
WHERE NOT EXISTS (SELECT 1 FROM ades_learning_paths LIMIT 1);
