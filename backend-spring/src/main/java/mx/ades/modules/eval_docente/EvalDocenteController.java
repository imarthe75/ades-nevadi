package mx.ades.modules.eval_docente;

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

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/eval-docente")
@RequiredArgsConstructor
public class EvalDocenteController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Data
    public static class EvaluacionCreate {
        private UUID profesorId;
        private UUID cicloEscolarId;
        private UUID evaluadorId;
        private String tipoEvaluador;
        private String comentarios;
    }

    @Data
    public static class CriterioCalificacion {
        private UUID criterioId;
        private Integer calificacion;
        private String observacion;
    }

    @GetMapping("/criterios")
    public ResponseEntity<List<Map<String, Object>>> listarCriterios(
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        String sql = "SELECT id, nombre_criterio, descripcion, categoria, peso_porcentual, escala_min, escala_max " +
                "FROM ades_criterios_eval_docente WHERE is_active = TRUE ORDER BY categoria, nombre_criterio";
        return ResponseEntity.ok(jdbc.queryForList(sql));
    }

    @GetMapping("/profesor/{profesorId}/resumen")
    public ResponseEntity<Map<String, Object>> resumenDocente(
            @PathVariable("profesorId") UUID profesorId,
            @RequestParam("ciclo_id") UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT tipo_evaluador, AVG(calificacion_global) AS promedio, COUNT(*) AS total " +
                "FROM ades_evaluacion_docente " +
                "WHERE profesor_id = ? AND ciclo_escolar_id = ? " +
                "AND is_active = TRUE AND estatus != 'BORRADOR' " +
                "GROUP BY tipo_evaluador";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, profesorId, cicloId);

        Map<String, Double> porTipo = new HashMap<>();
        long total = 0;
        double sumaPromedios = 0.0;

        for (Map<String, Object> r : rows) {
            String tipo = (String) r.get("tipo_evaluador");
            double prom = r.get("promedio") != null ? ((Number) r.get("promedio")).doubleValue() : 0.0;
            long count = r.get("total") != null ? ((Number) r.get("total")).longValue() : 0;
            
            double roundedProm = Math.round(prom * 100.0) / 100.0;
            porTipo.put(tipo, roundedProm);
            total += count;
            sumaPromedios += roundedProm;
        }

        Double promedioGlobal = porTipo.isEmpty() ? null : Math.round((sumaPromedios / porTipo.size()) * 100.0) / 100.0;

        Map<String, Object> res = new HashMap<>();
        res.put("profesor_id", profesorId.toString());
        res.put("ciclo_escolar_id", cicloId.toString());
        res.put("total_evaluaciones", total);
        res.put("promedio_global", promedioGlobal);
        res.put("por_tipo", porTipo);

        return ResponseEntity.ok(res);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearEvaluacion(
            @RequestBody EvaluacionCreate data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID evalId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_evaluacion_docente " +
                "(id, profesor_id, ciclo_escolar_id, evaluador_id, tipo_evaluador, comentarios, estatus, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'BORRADOR', ?, ?)",
                evalId, data.getProfesorId(), data.getCicloEscolarId(), data.getEvaluadorId(),
                data.getTipoEvaluador(), data.getComentarios(), user.getUsername(), user.getUsername()
        );

        // Fetch back to return matching the python output
        Map<String, Object> inserted = jdbc.queryForMap(
                "SELECT id, profesor_id, ciclo_escolar_id, evaluador_id, tipo_evaluador, " +
                "fecha_evaluacion, calificacion_global, comentarios, estatus " +
                "FROM ades_evaluacion_docente WHERE id = ?", evalId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(inserted);
    }

    @PostMapping("/{evalId}/criterios")
    public ResponseEntity<Map<String, Object>> guardarCriterios(
            @PathVariable("evalId") UUID evalId,
            @RequestBody List<CriterioCalificacion> criterios,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> evalRows = jdbc.queryForList(
                "SELECT estatus FROM ades_evaluacion_docente WHERE id = ?", evalId);
        if (evalRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada");
        }
        String estatus = (String) evalRows.get(0).get("estatus");
        if ("APROBADA".equalsIgnoreCase(estatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La evaluación ya está aprobada");
        }

        // Upsert criteria ratings
        for (CriterioCalificacion c : criterios) {
            jdbc.update(
                "INSERT INTO ades_eval_docente_criterios (evaluacion_id, criterio_id, calificacion, observacion) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (evaluacion_id, criterio_id) " +
                "DO UPDATE SET calificacion = EXCLUDED.calificacion, observacion = EXCLUDED.observacion",
                evalId, c.getCriterioId(), c.getCalificacion(), c.getObservacion()
            );
        }

        // Recalculate average weighted score
        jdbc.update(
            "UPDATE ades_evaluacion_docente " +
            "SET calificacion_global = (" +
            "  SELECT ROUND(SUM(edc.calificacion * cr.peso_porcentual) / SUM(cr.peso_porcentual), 2) " +
            "  FROM ades_eval_docente_criterios edc " +
            "  JOIN ades_criterios_eval_docente cr ON cr.id = edc.criterio_id " +
            "  WHERE edc.evaluacion_id = ?" +
            ") " +
            "WHERE id = ?",
            evalId, evalId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "eval_id", evalId.toString()));
    }

    @PatchMapping("/{evalId}/enviar")
    public ResponseEntity<Map<String, Object>> enviarEvaluacion(
            @PathVariable("evalId") UUID evalId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        int updated = jdbc.update(
                "UPDATE ades_evaluacion_docente SET estatus = 'ENVIADA', usuario_modificacion = ? " +
                "WHERE id = ? AND estatus = 'BORRADOR'",
                user.getUsername(), evalId
        );

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo enviar la evaluación o ya fue enviada");
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }
}
