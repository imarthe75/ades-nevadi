package mx.ades.modules.gradebook;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.sql.Array;
import java.sql.Connection;
import java.util.*;

@RestController
@RequestMapping("/api/v1/gradebook")
@RequiredArgsConstructor
public class GradebookController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final int MIN_JUSTIFICACION = 20;

    @Data
    public static class AjusteIn {
        private Double ajusteManual;
        private String justificacionAjuste;
    }

    @GetMapping("/periodo/{periodoId}/grupo/{grupoId}")
    public ResponseEntity<List<Map<String, Object>>> tablaCalificacionesGrupo(
            @PathVariable("periodoId") UUID periodoId,
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT p.nombre, p.apellido_paterno, p.apellido_materno, " +
                "est.matricula, " +
                "cp.materia_id, " +
                "m.nombre_materia, " +
                "cp.score_por_item, " +
                "cp.calificacion_calculada, " +
                "cp.ajuste_manual, " +
                "cp.calificacion_final, " +
                "cp.cerrada, " +
                "cp.fecha_calculo, " +
                "cp.id AS cal_periodo_id, " +
                "ne.escala_maxima, " +
                "ne.minimo_aprobatorio " +
                "FROM ades_inscripciones i " +
                "JOIN ades_estudiantes est ON est.id = i.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "LEFT JOIN ades_calificaciones_periodo cp " +
                "     ON cp.estudiante_id = i.estudiante_id " +
                "    AND cp.grupo_id = i.grupo_id " +
                "    AND cp.periodo_evaluacion_id = ? " +
                "LEFT JOIN ades_materias m ON m.id = cp.materia_id " +
                "LEFT JOIN ades_grados gr ON gr.id = ( " +
                "    SELECT g.grado_id FROM ades_grupos g WHERE g.id = i.grupo_id " +
                ") " +
                "LEFT JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "WHERE i.grupo_id = ? AND i.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(periodoId);
        params.add(grupoId);

        if (materiaId != null) {
            sql.append("AND cp.materia_id = ? ");
            params.add(materiaId);
        }

        sql.append("ORDER BY p.apellido_paterno, p.nombre, m.nombre_materia");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/alumno/{alumnoId}/boleta")
    public ResponseEntity<List<Map<String, Object>>> boletaAlumno(
            @PathVariable("alumnoId") UUID alumnoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT m.nombre_materia, " +
                "pe.nombre_periodo, pe.numero_periodo, " +
                "cp.score_por_item, " +
                "cp.calificacion_calculada, " +
                "cp.ajuste_manual, " +
                "cp.calificacion_final, " +
                "cp.cerrada, " +
                "ne.escala_maxima, " +
                "ne.minimo_aprobatorio, " +
                "(cp.calificacion_final >= ne.minimo_aprobatorio) AS acreditado " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_materias m ON m.id = cp.materia_id " +
                "JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
                "JOIN ades_grados gr ON gr.id = ( " +
                "    SELECT g.grado_id FROM ades_grupos g WHERE g.id = cp.grupo_id " +
                ") " +
                "JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "WHERE cp.estudiante_id = ? AND cp.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(alumnoId);

        if (periodoId != null) {
            sql.append("AND cp.periodo_evaluacion_id = ? ");
            params.add(periodoId);
        }
        if (cicloId != null) {
            sql.append("AND pe.ciclo_escolar_id = ? ");
            params.add(cicloId);
        }

        sql.append("ORDER BY m.nombre_materia, pe.numero_periodo");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping("/{calPeriodoId}/ajuste-manual")
    public ResponseEntity<Map<String, Object>> ajusteManual(
            @PathVariable("calPeriodoId") UUID calPeriodoId,
            @RequestBody AjusteIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (body.getJustificacionAjuste() == null || body.getJustificacionAjuste().trim().length() < MIN_JUSTIFICACION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La justificación debe tener al menos " + MIN_JUSTIFICACION + " caracteres");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT cerrada, calificacion_calculada FROM ades_calificaciones_periodo WHERE id = ? AND is_active = TRUE",
                calPeriodoId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de calificación no encontrado");
        }
        Map<String, Object> cal = rows.get(0);
        boolean cerrada = (Boolean) cal.get("cerrada");
        double calCalculada = cal.get("calificacion_calculada") != null ? ((Number) cal.get("calificacion_calculada")).doubleValue() : 0.0;

        if (cerrada) {
            boolean isAdmin = user.getRoles().contains("ADMIN_GLOBAL") || user.getRoles().contains("ADMIN_PLANTEL");
            if (!isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Calificación cerrada. Solo ADMIN puede modificarla.");
            }
        }

        double calFinal = Math.round((calCalculada + body.getAjusteManual()) * 100.0) / 100.0;

        jdbc.update(
                "UPDATE ades_calificaciones_periodo " +
                "SET ajuste_manual = ?, " +
                "    justificacion_ajuste = ?, " +
                "    calificacion_final = ?, " +
                "    fecha_modificacion = CURRENT_TIMESTAMP, " +
                "    row_version = row_version + 1, " +
                "    usuario_modificacion = ? " +
                "WHERE id = ?",
                body.getAjusteManual(), body.getJustificacionAjuste().trim(), calFinal, user.getUsername(), calPeriodoId
        );

        return ResponseEntity.ok(Map.of("message", "Ajuste aplicado", "calificacion_final", calFinal));
    }

    @PostMapping("/periodo/{periodoId}/recalcular-todo")
    public ResponseEntity<Map<String, Object>> recalcularPeriodo(
            @PathVariable("periodoId") UUID periodoId,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT i.estudiante_id, i.grupo_id, ad.materia_id " +
                "FROM ades_inscripciones i " +
                "JOIN ades_grupos g ON g.id = i.grupo_id " +
                "JOIN ades_asignaciones_docentes ad ON ad.grupo_id = i.grupo_id " +
                "WHERE ad.ciclo_escolar_id = ( " +
                "    SELECT ciclo_escolar_id FROM ades_periodos_evaluacion WHERE id = ? " +
                ") " +
                "AND i.is_active = TRUE AND g.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(periodoId);

        if (grupoId != null) {
            sql.append("AND i.grupo_id = ? ");
            params.add(grupoId);
        }

        List<Map<String, Object>> combos = jdbc.queryForList(sql.toString(), params.toArray());

        List<UUID> eids = new ArrayList<>();
        List<UUID> gids = new ArrayList<>();
        List<UUID> mids = new ArrayList<>();
        List<UUID> pids = new ArrayList<>();

        for (Map<String, Object> c : combos) {
            UUID mid = (UUID) c.get("materia_id");
            if (materiaId != null && !materiaId.equals(mid)) {
                continue;
            }
            eids.add((UUID) c.get("estudiante_id"));
            gids.add((UUID) c.get("grupo_id"));
            mids.add(mid);
            pids.add(periodoId);
        }

        if (eids.isEmpty()) {
            return ResponseEntity.ok(Map.of("recalculados", 0));
        }

        // Execute bulk calculation using Postgres arrays to avoid N+1 queries
        jdbc.execute((Connection con) -> {
            Array arrayE = con.createArrayOf("uuid", eids.toArray());
            Array arrayG = con.createArrayOf("uuid", gids.toArray());
            Array arrayM = con.createArrayOf("uuid", mids.toArray());
            Array arrayP = con.createArrayOf("uuid", pids.toArray());

            jdbc.update(
                "SELECT calcular_calificacion_periodo(e, g, m, p) " +
                "FROM unnest(?, ?, ?, ?) AS t(e uuid, g uuid, m uuid, p uuid)",
                arrayE, arrayG, arrayM, arrayP
            );
            return null;
        });

        return ResponseEntity.ok(Map.of("recalculados", eids.size()));
    }

    @PostMapping("/{calPeriodoId}/cerrar")
    public ResponseEntity<Map<String, Object>> cerrarCalificacion(
            @PathVariable("calPeriodoId") UUID calPeriodoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        boolean allowed = user.getRoles().contains("ADMIN_GLOBAL") ||
                user.getRoles().contains("ADMIN_PLANTEL") ||
                user.getRoles().contains("DIRECTOR") ||
                user.getRoles().contains("COORDINADOR_ACADEMICO");

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permiso para cerrar períodos");
        }

        int updated = jdbc.update(
                "UPDATE ades_calificaciones_periodo " +
                "SET cerrada = TRUE, fecha_cierre = CURRENT_TIMESTAMP, " +
                "    fecha_modificacion = CURRENT_TIMESTAMP, usuario_modificacion = ? " +
                "WHERE id = ? AND cerrada = FALSE",
                user.getUsername(), calPeriodoId
        );

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calificación ya cerrada o no encontrada");
        }

        return ResponseEntity.ok(Map.of("message", "Período cerrado"));
    }

    @GetMapping("/grupo/{grupoId}/concentrado")
    public ResponseEntity<Map<String, Object>> concentradoGrupo(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam("periodo_id") UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT p.nombre || ' ' || p.apellido_paterno AS alumno, " +
                "est.matricula, " +
                "cp.materia_id, " +
                "m.nombre_materia, " +
                "cp.calificacion_final, " +
                "ne.minimo_aprobatorio, " +
                "(cp.calificacion_final < ne.minimo_aprobatorio) AS en_riesgo " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_estudiantes est ON est.id = cp.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "JOIN ades_materias m ON m.id = cp.materia_id " +
                "JOIN ades_grados gr ON gr.id = ( " +
                "    SELECT g.grado_id FROM ades_grupos g WHERE g.id = cp.grupo_id " +
                ") " +
                "JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "WHERE cp.grupo_id = ? " +
                "AND cp.periodo_evaluacion_id = ? " +
                "AND cp.is_active = TRUE " +
                "ORDER BY p.apellido_paterno, m.nombre_materia";

        List<Map<String, Object>> data = jdbc.queryForList(sql, grupoId, periodoId);

        Map<String, Map<String, Object>> materias = new HashMap<>();
        for (Map<String, Object> row : data) {
            String mn = (String) row.get("nombre_materia");
            Double val = row.get("calificacion_final") != null ? ((Number) row.get("calificacion_final")).doubleValue() : null;
            boolean enRiesgo = (Boolean) row.get("en_riesgo");

            materias.computeIfAbsent(mn, k -> {
                Map<String, Object> map = new HashMap<>();
                map.put("calificaciones", new ArrayList<Double>());
                map.put("en_riesgo", 0);
                return map;
            });

            if (val != null) {
                ((List<Double>) materias.get(mn).get("calificaciones")).add(val);
            }
            if (enRiesgo) {
                int r = (Integer) materias.get(mn).get("en_riesgo");
                materias.get(mn).put("en_riesgo", r + 1);
            }
        }

        Map<String, Map<String, Object>> promedios = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : materias.entrySet()) {
            List<Double> cals = (List<Double>) entry.getValue().get("calificaciones");
            int enRiesgo = (Integer) entry.getValue().get("en_riesgo");

            Double prom = null;
            if (!cals.isEmpty()) {
                double sum = 0;
                for (double d : cals) sum += d;
                prom = Math.round((sum / cals.size()) * 100.0) / 100.0;
            }

            Map<String, Object> promStats = new HashMap<>();
            promStats.put("promedio", prom);
            promStats.put("en_riesgo", enRiesgo);
            promedios.put(entry.getKey(), promStats);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("detalle", data);
        res.put("promedios_por_materia", promedios);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/grupo/{grupoId}/cobertura-curricular")
    public ResponseEntity<Map<String, Object>> coberturaCurricular(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT tm.id, tm.nombre_tema, tm.orden, " +
                "m.nombre_materia, " +
                "COUNT(t.id) AS num_actividades, " +
                "COUNT(t.id) > 0 AS tiene_evidencia " +
                "FROM ades_temas tm " +
                "JOIN ades_materias m ON m.id = tm.materia_id " +
                "LEFT JOIN ades_tareas t " +
                "       ON t.tema_id = tm.id AND t.grupo_id = ? AND t.is_active = TRUE " +
                "WHERE tm.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(grupoId);

        if (materiaId != null) {
            sql.append("AND tm.materia_id = ? ");
            params.add(materiaId);
        }

        sql.append("GROUP BY tm.id, tm.nombre_tema, tm.orden, m.nombre_materia ");
        sql.append("ORDER BY m.nombre_materia, tm.orden");

        List<Map<String, Object>> data = jdbc.queryForList(sql.toString(), params.toArray());

        int total = data.size();
        int conEvidencia = 0;
        for (Map<String, Object> r : data) {
            if (Boolean.TRUE.equals(r.get("tiene_evidencia"))) {
                conEvidencia++;
            }
        }

        double pct = total > 0 ? Math.round(((double) conEvidencia / total * 100.0) * 10.0) / 10.0 : 0.0;

        Map<String, Object> res = new HashMap<>();
        res.put("total_temas", total);
        res.put("con_evidencia", conEvidencia);
        res.put("sin_evidencia", total - conEvidencia);
        res.put("pct_cobertura", pct);
        res.put("temas", data);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/inconsistencias/{grupoId}")
    public ResponseEntity<Map<String, Object>> detectarInconsistencias(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT " +
                "    est.id        AS estudiante_id, " +
                "    per.nombre    AS nombre, " +
                "    per.apellido_paterno, " +
                "    est.matricula, " +
                "    cp.calificacion_final, " +
                "    cp.es_acreditado, " +
                "    COUNT(e.id) FILTER (WHERE e.calificacion_obtenida IS NOT NULL) AS entregas_calificadas, " +
                "    COUNT(t.id) AS total_actividades " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_estudiantes est ON est.id = cp.estudiante_id " +
                "JOIN ades_personas per    ON per.id = est.persona_id " +
                "LEFT JOIN ades_tareas t   ON t.grupo_id = ? AND t.is_active = TRUE " +
                "LEFT JOIN ades_tareas_entregas e ON e.tarea_id = t.id AND e.estudiante_id = est.id " +
                "WHERE cp.grupo_id = ? " +
                "  AND cp.is_active = TRUE " +
                "  AND cp.calificacion_final IS NOT NULL "
        );

        List<Object> params = new ArrayList<>();
        params.add(grupoId);
        params.add(grupoId);

        if (periodoId != null) {
            sql.append("AND cp.periodo_evaluacion_id = ? ");
            params.add(periodoId);
        }

        sql.append("GROUP BY est.id, per.nombre, per.apellido_paterno, est.matricula, " +
                "         cp.calificacion_final, cp.es_acreditado " +
                "HAVING cp.es_acreditado = TRUE AND COUNT(e.id) FILTER (WHERE e.calificacion_obtenida IS NOT NULL) = 0 " +
                "ORDER BY per.apellido_paterno, per.nombre");

        List<Map<String, Object>> inconsistencias = jdbc.queryForList(sql.toString(), params.toArray());

        Map<String, Object> res = new HashMap<>();
        res.put("grupo_id", grupoId.toString());
        res.put("total_inconsistencias", inconsistencias.size());
        res.put("descripcion", "Alumnos con calificación aprobatoria pero sin entregas calificadas");
        res.put("casos", inconsistencias);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/candidatos-extraordinario/{grupoId}")
    public ResponseEntity<Map<String, Object>> candidatosExtraordinario(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT " +
                "    est.id        AS estudiante_id, " +
                "    per.nombre    AS nombre, " +
                "    per.apellido_paterno, " +
                "    est.matricula, " +
                "    cp.calificacion_final, " +
                "    cp.calificacion_calculada, " +
                "    cp.es_acreditado, " +
                "    ne.minimo_aprobatorio, " +
                "    pe.nombre_periodo " +
                "FROM ades_calificaciones_periodo cp " +
                "JOIN ades_estudiantes est      ON est.id = cp.estudiante_id " +
                "JOIN ades_personas per         ON per.id = est.persona_id " +
                "JOIN ades_grupos g             ON g.id = cp.grupo_id " +
                "JOIN ades_grados gr            ON gr.id = g.grado_id " +
                "JOIN ades_niveles_educativos ne ON ne.id = gr.nivel_educativo_id " +
                "LEFT JOIN ades_periodos_evaluacion pe ON pe.id = cp.periodo_evaluacion_id " +
                "WHERE cp.grupo_id = ? " +
                "  AND cp.is_active = TRUE " +
                "  AND cp.es_acreditado = FALSE " +
                "  AND cp.calificacion_final IS NOT NULL "
        );

        List<Object> params = new ArrayList<>();
        params.add(grupoId);

        if (periodoId != null) {
            sql.append("AND cp.periodo_evaluacion_id = ? ");
            params.add(periodoId);
        }

        sql.append("ORDER BY cp.calificacion_final ASC, per.apellido_paterno");

        List<Map<String, Object>> candidatos = jdbc.queryForList(sql.toString(), params.toArray());

        Map<String, Object> res = new HashMap<>();
        res.put("grupo_id", grupoId.toString());
        res.put("total_candidatos", candidatos.size());
        res.put("descripcion", "Alumnos con calificación reprobatoria — candidatos a examen extraordinario");
        res.put("candidatos", candidatos);

        return ResponseEntity.ok(res);
    }
}
