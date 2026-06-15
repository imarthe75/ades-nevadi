package mx.ades.modules.evaluaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/evaluaciones")
@RequiredArgsConstructor
public class EvaluacionController {

    private final EvaluacionRepository repository;
    private final JdbcTemplate jdbc;
    private final AdesUserService userService;

    private static final String EVAL_SELECT =
        "SELECT e.id, e.nombre_evaluacion, e.descripcion, e.grupo_id, e.materia_id, " +
        "  e.periodo_evaluacion_id, e.fecha_evaluacion, e.tipo_evaluacion, e.puntaje_maximo, " +
        "  e.is_active, e.row_version, " +
        "  g.nombre_grupo, " +
        "  m.nombre_materia, " +
        "  pe.nombre_periodo, pe.numero_periodo, " +
        "  COUNT(c.id) AS total_calificados, " +
        "  ROUND(AVG(c.calificacion)::numeric, 2) AS promedio, " +
        "  COUNT(c.id) FILTER (WHERE c.calificacion >= 6) AS aprobados, " +
        "  COUNT(c.id) FILTER (WHERE c.calificacion < 6) AS reprobados " +
        "FROM ades_evaluaciones e " +
        "JOIN ades_grupos g ON g.id = e.grupo_id " +
        "JOIN ades_materias m ON m.id = e.materia_id " +
        "JOIN ades_periodos_evaluacion pe ON pe.id = e.periodo_evaluacion_id " +
        "LEFT JOIN ades_calificaciones_evaluaciones c ON c.evaluacion_id = e.id AND c.is_active = TRUE ";

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "grupo_id", required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(EVAL_SELECT + "WHERE e.is_active = TRUE ");
        List<Object> params = new ArrayList<>();
        if (grupoId != null) {
            sql.append("AND e.grupo_id = ? ");
            params.add(grupoId);
        }
        sql.append("GROUP BY e.id, g.nombre_grupo, m.nombre_materia, pe.nombre_periodo, pe.numero_periodo ");
        sql.append("ORDER BY e.fecha_evaluacion DESC");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Evaluacion> get(@PathVariable("id") UUID id) {
        Evaluacion eval = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada"));
        return ResponseEntity.ok(eval);
    }

    /** Returns ALL students in the group with their calificacion (null if not graded yet). */
    @GetMapping("/{id}/calificaciones")
    public ResponseEntity<List<Map<String, Object>>> calificaciones(
            @PathVariable("id") UUID evalId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        // Get grupo_id from evaluacion
        List<Map<String, Object>> evalRows = jdbc.queryForList(
            "SELECT grupo_id FROM ades_evaluaciones WHERE id = ?", evalId);
        if (evalRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada");
        UUID grupoId = (UUID) evalRows.get(0).get("grupo_id");

        // All students in group × their grade (LEFT JOIN)
        return ResponseEntity.ok(jdbc.queryForList(
            "SELECT est.id AS estudiante_id, " +
            "  CONCAT(pe.nombre, ' ', pe.apellido_paterno, CASE WHEN pe.apellido_materno IS NOT NULL THEN CONCAT(' ', pe.apellido_materno) ELSE '' END) AS nombre_alumno, " +
            "  est.matricula, " +
            "  c.id AS calificacion_id, c.calificacion, c.comentarios " +
            "FROM ades_inscripciones ins " +
            "JOIN ades_estudiantes est ON est.id = ins.estudiante_id " +
            "JOIN ades_personas pe ON pe.id = est.persona_id " +
            "LEFT JOIN ades_calificaciones_evaluaciones c ON c.evaluacion_id = ? AND c.estudiante_id = est.id AND c.is_active = TRUE " +
            "WHERE ins.grupo_id = ? AND ins.is_active = TRUE " +
            "ORDER BY pe.apellido_paterno, pe.nombre",
            evalId, grupoId));
    }

    @PostMapping("/{id}/calificaciones/bulk")
    public ResponseEntity<Map<String, Object>> bulkCalificaciones(
            @PathVariable("id") UUID evalId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        var user = userService.resolveUser(jwt);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> califs = (List<Map<String, Object>>) body.get("calificaciones");
        if (califs == null || califs.isEmpty()) {
            return ResponseEntity.ok(Map.of("updated", 0));
        }

        int updated = 0;
        for (Map<String, Object> c : califs) {
            UUID estudianteId = UUID.fromString(c.get("estudiante_id").toString());
            Object calVal = c.get("calificacion");
            if (calVal == null) continue;
            double calificacion = Double.parseDouble(calVal.toString());
            String comentarios = (String) c.get("comentarios");

            List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM ades_calificaciones_evaluaciones WHERE evaluacion_id = ? AND estudiante_id = ? AND is_active = TRUE",
                evalId, estudianteId);

            if (existing.isEmpty()) {
                jdbc.update(
                    "INSERT INTO ades_calificaciones_evaluaciones (evaluacion_id, estudiante_id, calificacion, comentarios, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    evalId, estudianteId, calificacion, comentarios, user.getUsername(), user.getUsername());
            } else {
                UUID calId = (UUID) existing.get(0).get("id");
                jdbc.update(
                    "UPDATE ades_calificaciones_evaluaciones SET calificacion = ?, comentarios = ?, " +
                    "usuario_modificacion = ? WHERE id = ?",
                    calificacion, comentarios, user.getUsername(), calId);
            }
            updated++;
        }

        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping
    public ResponseEntity<Evaluacion> create(@RequestBody Evaluacion evaluacion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(evaluacion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Evaluacion> update(@PathVariable("id") UUID id, @RequestBody Evaluacion update) {
        Evaluacion eval = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación no encontrada"));

        eval.setNombreEvaluacion(update.getNombreEvaluacion());
        eval.setDescripcion(update.getDescripcion());
        eval.setGrupoId(update.getGrupoId());
        eval.setMateriaId(update.getMateriaId());
        eval.setPeriodoEvaluacionId(update.getPeriodoEvaluacionId());
        eval.setFechaEvaluacion(update.getFechaEvaluacion());
        eval.setTipoEvaluacion(update.getTipoEvaluacion());
        eval.setPuntajeMaximo(update.getPuntajeMaximo());
        eval.setIsActive(update.getIsActive());

        return ResponseEntity.ok(repository.save(eval));
    }
}
