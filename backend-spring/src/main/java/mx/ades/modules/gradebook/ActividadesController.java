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

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/actividades")
@RequiredArgsConstructor
public class ActividadesController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    @Data
    public static class ActividadIn {
        private String titulo;
        private String descripcion;
        private UUID grupoId;
        private UUID materiaId;
        private UUID periodoEvaluacionId;
        private String tipoItem = "tarea";
        private UUID temaId;
        private UUID planTrabajoId;
        private UUID rubricaId;
        private LocalDate fechaAsignacion;
        private LocalDate fechaEntrega;
        private LocalDate fechaExamen;
        private Double puntajeMaximo = 10.0;
        private String instruccionesUrl;
        private Boolean permiteEntregaTarde = false;
    }

    @Data
    public static class CalificarMasivoItem {
        private UUID alumnoId;
        private Double calificacion;
        private String comentario;
    }

    @GetMapping("/grupo/{grupoId}")
    public ResponseEntity<List<Map<String, Object>>> actividadesDeGrupo(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "tipo_item", required = false) String tipoItem,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.titulo, t.descripcion, t.tipo_item, " +
                "t.fecha_asignacion, t.fecha_entrega, t.fecha_examen, " +
                "t.puntaje_maximo, t.permite_entrega_tarde, " +
                "t.instrucciones_url, " +
                "m.nombre_materia, " +
                "te_stats.total_alumnos, " +
                "te_stats.entregadas, " +
                "te_stats.calificadas, " +
                "pe.nombre_periodo, " +
                "tm.nombre_tema " +
                "FROM ades_tareas t " +
                "JOIN ades_materias m ON m.id = t.materia_id " +
                "LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id " +
                "LEFT JOIN ades_temas tm ON tm.id = t.tema_id " +
                "LEFT JOIN LATERAL ( " +
                "    SELECT COUNT(*) AS total_alumnos, " +
                "           COUNT(*) FILTER (WHERE te.estatus_entrega IN ('ENTREGADA','CALIFICADA')) AS entregadas, " +
                "           COUNT(*) FILTER (WHERE te.estatus_entrega = 'CALIFICADA') AS calificadas " +
                "      FROM ades_tareas_entregas te " +
                "     WHERE te.tarea_id = t.id " +
                ") te_stats ON TRUE " +
                "WHERE t.grupo_id = ? AND t.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(grupoId);

        if (materiaId != null) {
            sql.append("AND t.materia_id = ? ");
            params.add(materiaId);
        }
        if (periodoId != null) {
            sql.append("AND t.periodo_evaluacion_id = ? ");
            params.add(periodoId);
        }
        if (tipoItem != null && !tipoItem.isBlank()) {
            sql.append("AND t.tipo_item = ? ");
            params.add(tipoItem);
        }

        sql.append("ORDER BY t.fecha_entrega, t.tipo_item");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearActividad(
            @RequestBody ActividadIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        UUID tareaId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_tareas " +
                "(id, titulo, descripcion, grupo_id, materia_id, periodo_evaluacion_id, tipo_item, tema_id, " +
                "plan_trabajo_id, rubrica_id, fecha_asignacion, fecha_entrega, fecha_examen, puntaje_maximo, " +
                "instrucciones_url, permite_entrega_tarde, origen, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?)",
                tareaId, body.getTitulo(), body.getDescripcion(), body.getGrupoId(), body.getMateriaId(),
                body.getPeriodoEvaluacionId(), body.getTipoItem(), body.getTemaId(), body.getPlanTrabajoId(),
                body.getRubricaId(), body.getFechaAsignacion(), body.getFechaEntrega(), body.getFechaExamen(),
                body.getPuntajeMaximo(), body.getInstruccionesUrl(), body.getPermiteEntregaTarde(),
                user.getUsername(), user.getUsername()
        );

        // Generate delivery slots for all active group enrollments
        List<Map<String, Object>> alumnos = jdbc.queryForList(
                "SELECT estudiante_id FROM ades_inscripciones WHERE grupo_id = ? AND is_active = TRUE",
                body.getGrupoId()
        );

        int slotsCreados = 0;
        for (Map<String, Object> a : alumnos) {
            UUID estudianteId = (UUID) a.get("estudiante_id");
            jdbc.update(
                    "INSERT INTO ades_tareas_entregas " +
                    "(id, tarea_id, estudiante_id, estatus_entrega, usuario_creacion, usuario_modificacion) " +
                    "VALUES (?, ?, ?, 'PENDIENTE', ?, ?) " +
                    "ON CONFLICT (tarea_id, estudiante_id) DO NOTHING",
                    UUID.randomUUID(), tareaId, estudianteId, user.getUsername(), user.getUsername()
            );
            slotsCreados++;
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", tareaId.toString(),
                "slots_creados", slotsCreados,
                "message", "Actividad creada y slots generados"
        ));
    }

    @GetMapping("/{actividadId}/entregas")
    public ResponseEntity<List<Map<String, Object>>> entregasDeActividad(
            @PathVariable("actividadId") UUID actividadId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        String sql = "SELECT te.id, te.estudiante_id, te.estatus_entrega, " +
                "te.fecha_entrega, te.es_tarde, " +
                "te.calificacion_obtenida, te.comentario_profesor, " +
                "te.archivo_url, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "est.matricula " +
                "FROM ades_tareas_entregas te " +
                "JOIN ades_estudiantes est ON est.id = te.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "WHERE te.tarea_id = ? " +
                "ORDER BY p.apellido_paterno, p.nombre";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, actividadId);
        return ResponseEntity.ok(rows);
    }

    @PatchMapping("/{actividadId}/calificar-masivo")
    public ResponseEntity<Map<String, Object>> calificarMasivo(
            @PathVariable("actividadId") UUID actividadId,
            @RequestBody List<CalificarMasivoItem> items,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        int actualizados = 0;
        for (CalificarMasivoItem item : items) {
            int r = jdbc.update(
                    "UPDATE ades_tareas_entregas " +
                    "SET calificacion_obtenida = ?, " +
                    "    comentario_profesor = ?, " +
                    "    calificado_por = ?, " +
                    "    fecha_calificacion_docente = CURRENT_TIMESTAMP, " +
                    "    estatus_entrega = 'CALIFICADA', " +
                    "    fecha_modificacion = CURRENT_TIMESTAMP, " +
                    "    row_version = row_version + 1, " +
                    "    usuario_modificacion = ? " +
                    "WHERE tarea_id = ? AND estudiante_id = ?",
                    item.getCalificacion(), item.getComentario(), user.getId(), user.getUsername(),
                    actividadId, item.getAlumnoId()
            );
            actualizados += r;
        }

        return ResponseEntity.ok(Map.of("actualizados", actualizados));
    }
}
