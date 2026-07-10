package mx.ades.modules.grade_analytics.query;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo grade_analytics.
 * Expone consultas de tendencias, distribución de calificaciones, alumnos en riesgo,
 * resumen por plantel, cobertura curricular y alertas de umbral académico.
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class GradeAnalyticsQueryService {

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> tendenciasGrupo(UUID grupoId) {
        return jdbc.queryForList(
            "SELECT nombre_materia, nombre_periodo, numero_periodo, alumnos_evaluados, " +
            "ROUND(promedio, 2) AS promedio, ROUND(minimo, 2) AS minimo, ROUND(maximo, 2) AS maximo, " +
            "aprobados, reprobados, " +
            "CASE WHEN alumnos_evaluados > 0 " +
            "     THEN ROUND(aprobados::numeric / alumnos_evaluados * 100, 1) " +
            "     ELSE 0 END AS pct_aprobados " +
            "FROM ades_bi.mv_calificaciones_grupo WHERE grupo_id = ? " +
            "ORDER BY numero_periodo, nombre_materia", grupoId);
    }

    public List<Map<String, Object>> distribucion(UUID grupoId, Integer numeroPeriodo) {
        StringBuilder sql = new StringBuilder(
            "SELECT CASE " +
            "    WHEN cp.calificacion < 6  THEN '< 6.0' " +
            "    WHEN cp.calificacion < 7  THEN '6.0 – 6.9' " +
            "    WHEN cp.calificacion < 8  THEN '7.0 – 7.9' " +
            "    WHEN cp.calificacion < 9  THEN '8.0 – 8.9' " +
            "    WHEN cp.calificacion < 10 THEN '9.0 – 9.9' " +
            "    ELSE '10.0' " +
            "END AS rango, COUNT(*) AS total_alumnos, " +
            "ROUND(COUNT(*)::numeric / SUM(COUNT(*)) OVER() * 100, 1) AS pct " +
            "FROM ades_calificaciones_periodo cp " +
            "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
            "JOIN ades_inscripciones i ON i.id = cp.inscripcion_id " +
            "WHERE i.grupo_id = ? AND cp.calificacion IS NOT NULL ");
        List<Object> params = new ArrayList<>();
        params.add(grupoId);
        if (numeroPeriodo != null) {
            sql.append("AND pe.numero_periodo = ? ");
            params.add(numeroPeriodo);
        }
        sql.append("GROUP BY rango ORDER BY MIN(cp.calificacion)");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> alumnosEnRiesgo(UUID plantelId, UUID grupoId, String nivelRiesgo, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT estudiante_id, nombre_alumno, nombre_grupo, nombre_grado, nombre_plantel, nombre_nivel, " +
            "ROUND(promedio_general, 2) AS promedio_general, ROUND(pct_asistencia, 1) AS pct_asistencia, " +
            "materias_reprobadas, nivel_riesgo FROM ades_bi.mv_riesgo_academico WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append("AND nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }
        if (grupoId != null) { sql.append("AND grupo_id = ? "); params.add(grupoId); }
        if (nivelRiesgo != null && !nivelRiesgo.isBlank()) { sql.append("AND nivel_riesgo = ? "); params.add(nivelRiesgo); }
        sql.append("ORDER BY CASE nivel_riesgo WHEN 'ALTO' THEN 1 WHEN 'MEDIO' THEN 2 ELSE 3 END, promedio_general ASC LIMIT ?");
        params.add(limit);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Cacheable(value = "analytics", key = "'resumenPlantel_' + #plantelId")
    public List<Map<String, Object>> resumenPlantel(UUID plantelId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ades_bi.mv_resumen_plantel ");
        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append("WHERE nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }
        sql.append("ORDER BY nombre_plantel, nombre_nivel");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> coberturaCurricular(UUID plantelId, UUID grupoId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ades_bi.mv_cobertura_curricular WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (plantelId != null) {
            sql.append("AND nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }
        if (grupoId != null) { sql.append("AND grupo_id = ? "); params.add(grupoId); }
        sql.append("ORDER BY nombre_plantel, nombre_grupo, nombre_materia");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> alertasUmbral(double umbral, UUID plantelId, UUID grupoId, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT estudiante_id, nombre_alumno, nombre_grupo, nombre_grado, nombre_plantel, " +
            "ROUND(promedio_general, 2) AS promedio_general, ROUND(pct_asistencia, 1) AS pct_asistencia, " +
            "materias_reprobadas, nivel_riesgo FROM ades_bi.mv_riesgo_academico WHERE promedio_general < ? ");
        List<Object> params = new ArrayList<>();
        params.add(umbral);
        if (plantelId != null) {
            sql.append("AND nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }
        if (grupoId != null) { sql.append("AND grupo_id = ? "); params.add(grupoId); }
        sql.append("ORDER BY promedio_general ASC LIMIT ?");
        params.add(limit);
        return jdbc.queryForList(sql.toString(), params.toArray());
    }
}
