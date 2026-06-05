-- =============================================================================
-- Migración 016 — Superset BI: usuario solo-lectura + vistas adicionales
-- =============================================================================
-- Ajusta el usuario superset_ro para usar la contraseña del entorno.
-- Las vistas materializadas ades_bi.mv_* se crearon en migración 003.
-- Esta migración agrega la vista mv_asistencia_mensual y una función
-- para refrescar todas las vistas en lote (útil para Celery Beat).
-- =============================================================================

-- ── 1. Actualizar contraseña superset_ro ──────────────────────────────────────
-- La contraseña real se inyecta desde el script de init o manualmente:
--   psql -c "ALTER USER superset_ro WITH PASSWORD '$SUPERSET_RO_PASSWORD';"
-- Por defecto se deja el placeholder del primer arranque.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'superset_ro') THEN
        CREATE ROLE superset_ro LOGIN PASSWORD 'superset_ro_changeme';
    END IF;
END $$;

GRANT USAGE ON SCHEMA ades_bi TO superset_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA ades_bi TO superset_ro;
GRANT USAGE ON SCHEMA public TO superset_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO superset_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA ades_bi GRANT SELECT ON TABLES TO superset_ro;

-- ── 2. Vista: asistencia mensual agregada (para gráfica de tendencia) ──────────
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_asistencia_mensual;
CREATE MATERIALIZED VIEW ades_bi.mv_asistencia_mensual AS
SELECT
    date_trunc('month', a.fecha)::date                    AS mes,
    g.plantel_id,
    p.nombre                                               AS nombre_plantel,
    ne.nombre                                              AS nivel,
    g.id                                                   AS grupo_id,
    CONCAT(ne.nombre, ' ', gr.numero_grado, '° ', g.nombre_grupo) AS nombre_grupo,
    COUNT(*)                                               AS total_registros,
    SUM(CASE WHEN a.presente THEN 1 ELSE 0 END)           AS total_presentes,
    SUM(CASE WHEN NOT a.presente THEN 1 ELSE 0 END)       AS total_ausentes,
    ROUND(
        100.0 * SUM(CASE WHEN a.presente THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 2
    )                                                      AS pct_asistencia
FROM ades_asistencias a
JOIN ades_clases     cl ON cl.id  = a.clase_id
JOIN ades_grupos      g ON g.id   = cl.grupo_id
JOIN ades_grados     gr ON gr.id  = g.grado_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
JOIN ades_planteles   p ON p.id   = g.plantel_id
GROUP BY 1, 2, 3, 4, 5, 6
WITH DATA;

CREATE UNIQUE INDEX idx_mv_asistencia_mensual
    ON ades_bi.mv_asistencia_mensual (mes, grupo_id);

GRANT SELECT ON ades_bi.mv_asistencia_mensual TO superset_ro;

-- ── 3. Vista: top 10 materias con mayor reprobación ───────────────────────────
DROP MATERIALIZED VIEW IF EXISTS ades_bi.mv_reprobacion_materias;
CREATE MATERIALIZED VIEW ades_bi.mv_reprobacion_materias AS
SELECT
    m.nombre                                          AS materia,
    ne.nombre                                         AS nivel,
    cp.numero_periodo,
    COUNT(*)                                          AS total_calificaciones,
    SUM(CASE WHEN cp.calificacion_final < 6 THEN 1 ELSE 0 END) AS reprobados,
    ROUND(
        100.0 * SUM(CASE WHEN cp.calificacion_final < 6 THEN 1 ELSE 0 END)
        / NULLIF(COUNT(*), 0), 2
    )                                                 AS pct_reprobacion,
    ROUND(AVG(cp.calificacion_final), 2)              AS promedio
FROM ades_calificaciones_periodo cp
JOIN ades_inscripciones       ins ON ins.id = cp.inscripcion_id
JOIN ades_grupos                g ON g.id   = ins.grupo_id
JOIN ades_grados               gr ON gr.id  = g.grado_id
JOIN ades_niveles_educativos   ne ON ne.id  = gr.nivel_educativo_id
JOIN ades_materias_plan        mp ON mp.id  = cp.materia_plan_id
JOIN ades_materias              m ON m.id   = mp.materia_id
WHERE cp.calificacion_final IS NOT NULL
GROUP BY 1, 2, 3
WITH DATA;

CREATE INDEX idx_mv_reprobacion_materias
    ON ades_bi.mv_reprobacion_materias (nivel, numero_periodo);

GRANT SELECT ON ades_bi.mv_reprobacion_materias TO superset_ro;

-- ── 4. Función para refrescar todas las vistas materializadas ─────────────────
CREATE OR REPLACE FUNCTION ades_bi.refresh_all_views()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_asistencia_diaria;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_calificaciones_grupo;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_riesgo_academico;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_resumen_plantel;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_cobertura_curricular;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_asistencia_mensual;
    REFRESH MATERIALIZED VIEW CONCURRENTLY ades_bi.mv_reprobacion_materias;
END;
$$;

COMMENT ON FUNCTION ades_bi.refresh_all_views() IS
  'Refresca todas las vistas materializadas de ades_bi. Llamar desde Celery Beat (diario, 2am).';
