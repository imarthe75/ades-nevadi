-- =============================================================================
-- 132_mv_asistencia_diaria.sql
-- Recrea ades_bi.mv_asistencia_diaria — referenciada por el dataset Superset
-- "mv_asistencia_diaria" (id 4, chart "Tendencia Asistencia Diaria") pero
-- nunca existió en este esquema (mismo patrón de vista/columna fantasma visto
-- en mv_asistencia_mensual). Sin ella, 2 de los 4 dashboards de BI (Mi
-- Plantel, Vista Docente) tenían un chart roto.
-- =============================================================================

CREATE MATERIALIZED VIEW ades_bi.mv_asistencia_diaria AS
SELECT
    cl.fecha_clase                                                            AS fecha,
    gr.plantel_id,
    pl.nombre_plantel,
    ne.nombre_nivel                                                           AS nivel,
    COUNT(*)                                                                  AS total_registros,
    SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)        AS total_presentes,
    ROUND(
        100.0 * SUM(CASE WHEN a.estatus_asistencia = 'PRESENTE' THEN 1 ELSE 0 END)
        / NULLIF(COUNT(*), 0), 2
    )                                                                          AS pct_asistencia
FROM ades_asistencias a
JOIN ades_clases cl            ON cl.id = a.clase_id
JOIN ades_grupos g              ON g.id = cl.grupo_id
JOIN ades_grados gr             ON gr.id = g.grado_id
JOIN ades_planteles pl          ON pl.id = gr.plantel_id
JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id
WHERE a.is_active = TRUE
GROUP BY cl.fecha_clase, gr.plantel_id, pl.nombre_plantel, ne.nombre_nivel
WITH NO DATA;

CREATE UNIQUE INDEX idx_mv_asistencia_diaria
    ON ades_bi.mv_asistencia_diaria (fecha, plantel_id);

GRANT SELECT ON ades_bi.mv_asistencia_diaria TO superset_ro;
