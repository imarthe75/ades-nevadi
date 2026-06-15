package mx.ades.modules.grade_analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/grade-analytics")
@RequiredArgsConstructor
public class GradeAnalyticsController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @GetMapping("/tendencias/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> tendenciasGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT nombre_materia, nombre_periodo, numero_periodo, alumnos_evaluados, " +
                "ROUND(promedio, 2) AS promedio, ROUND(minimo, 2) AS minimo, ROUND(maximo, 2) AS maximo, " +
                "aprobados, reprobados, " +
                "CASE WHEN alumnos_evaluados > 0 " +
                "     THEN ROUND(aprobados::numeric / alumnos_evaluados * 100, 1) " +
                "     ELSE 0 END AS pct_aprobados " +
                "FROM ades_bi.mv_calificaciones_grupo " +
                "WHERE grupo_id = ? " +
                "ORDER BY numero_periodo, nombre_materia";

        return ResponseEntity.ok(jdbc.queryForList(sql, grupoId));
    }

    @GetMapping("/distribucion/{grupo_id}")
    public ResponseEntity<List<Map<String, Object>>> distribucionCalificaciones(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "numero_periodo", required = false) Integer numeroPeriodo,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT " +
                "    CASE " +
                "        WHEN cp.calificacion < 6  THEN '< 6.0' " +
                "        WHEN cp.calificacion < 7  THEN '6.0 – 6.9' " +
                "        WHEN cp.calificacion < 8  THEN '7.0 – 7.9' " +
                "        WHEN cp.calificacion < 9  THEN '8.0 – 8.9' " +
                "        WHEN cp.calificacion < 10 THEN '9.0 – 9.9' " +
                "        ELSE '10.0' " +
                "    END AS rango, " +
                "    COUNT(*) AS total_alumnos, " +
                "    ROUND(COUNT(*)::numeric / SUM(COUNT(*)) OVER() * 100, 1) AS pct " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
                "JOIN ades_inscripciones i ON i.id = cp.inscripcion_id " +
                "WHERE i.grupo_id = ? " +
                "  AND cp.calificacion IS NOT NULL "
        );

        List<Object> params = new ArrayList<>();
        params.add(grupoId);

        if (numeroPeriodo != null) {
            sql.append("  AND pe.numero_periodo = ? ");
            params.add(numeroPeriodo);
        }

        sql.append("GROUP BY rango ORDER BY MIN(cp.calificacion)");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/riesgo")
    public ResponseEntity<List<Map<String, Object>>> alumnosEnRiesgo(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "nivel_riesgo", required = false) String nivelRiesgo,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT estudiante_id, nombre_alumno, nombre_grupo, nombre_grado, nombre_plantel, nombre_nivel, " +
                "ROUND(promedio_general, 2) AS promedio_general, ROUND(pct_asistencia, 1) AS pct_asistencia, " +
                "materias_reprobadas, nivel_riesgo " +
                "FROM ades_bi.mv_riesgo_academico " +
                "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (plantelId != null) {
            sql.append("AND nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }
        if (grupoId != null) {
            sql.append("AND grupo_id = ? ");
            params.add(grupoId);
        }
        if (nivelRiesgo != null && !nivelRiesgo.isBlank()) {
            sql.append("AND nivel_riesgo = ? ");
            params.add(nivelRiesgo);
        }

        sql.append("ORDER BY CASE nivel_riesgo WHEN 'ALTO' THEN 1 WHEN 'MEDIO' THEN 2 ELSE 3 END, promedio_general ASC LIMIT ?");
        params.add(limit);

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/resumen-plantel")
    public ResponseEntity<List<Map<String, Object>>> resumenPlantel(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder("SELECT * FROM ades_bi.mv_resumen_plantel ");
        List<Object> params = new ArrayList<>();

        if (plantelId != null) {
            sql.append("WHERE nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }

        sql.append("ORDER BY nombre_plantel, nombre_nivel");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/cobertura")
    public ResponseEntity<List<Map<String, Object>>> coberturaCurricular(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder("SELECT * FROM ades_bi.mv_cobertura_curricular WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (plantelId != null) {
            sql.append("AND nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }
        if (grupoId != null) {
            sql.append("AND grupo_id = ? ");
            params.add(grupoId);
        }

        sql.append("ORDER BY nombre_plantel, nombre_grupo, nombre_materia");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/alertas-umbral")
    public ResponseEntity<List<Map<String, Object>>> alertasUmbral(
            @RequestParam(value = "umbral", defaultValue = "7.0") double umbral,
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT estudiante_id, nombre_alumno, nombre_grupo, nombre_grado, nombre_plantel, " +
                "ROUND(promedio_general, 2) AS promedio_general, ROUND(pct_asistencia, 1) AS pct_asistencia, " +
                "materias_reprobadas, nivel_riesgo " +
                "FROM ades_bi.mv_riesgo_academico " +
                "WHERE promedio_general < ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(umbral);

        if (plantelId != null) {
            sql.append("AND nombre_plantel = (SELECT nombre_plantel FROM ades_planteles WHERE id = ?) ");
            params.add(plantelId);
        }
        if (grupoId != null) {
            sql.append("AND grupo_id = ? ");
            params.add(grupoId);
        }

        sql.append("ORDER BY promedio_general ASC LIMIT ?");
        params.add(limit);

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }
}
