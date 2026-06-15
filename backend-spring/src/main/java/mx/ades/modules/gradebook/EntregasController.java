package mx.ades.modules.gradebook;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.evaluaciones.MinioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/entregas")
@RequiredArgsConstructor
public class EntregasController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final MinioService minioService;

    @Data
    public static class CalificarIn {
        private Double calificacion;
        private String comentario;
    }

    @GetMapping("/alumno/{alumnoId}")
    public ResponseEntity<List<Map<String, Object>>> entregasDelAlumno(
            @PathVariable("alumnoId") UUID alumnoId,
            @RequestParam(value = "periodo_id", required = false) UUID periodoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @RequestParam(value = "solo_pendientes", defaultValue = "false") boolean soloPendientes,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT te.id, te.tarea_id, te.estatus_entrega, " +
                "te.fecha_entrega, te.es_tarde, " +
                "te.calificacion_obtenida, te.comentario_profesor, " +
                "te.archivo_url, " +
                "te.fecha_calificacion_docente, " +
                "t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite, " +
                "t.puntaje_maximo, " +
                "m.nombre_materia, " +
                "pe.nombre_periodo, " +
                "(t.fecha_entrega < CURRENT_DATE AND te.estatus_entrega = 'PENDIENTE') AS vencida " +
                "FROM ades_tareas_entregas te " +
                "JOIN ades_tareas t ON t.id = te.tarea_id " +
                "JOIN ades_materias m ON m.id = t.materia_id " +
                "LEFT JOIN ades_periodos_evaluacion pe ON pe.id = t.periodo_evaluacion_id " +
                "WHERE te.estudiante_id = ? AND te.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(alumnoId);

        if (periodoId != null) {
            sql.append("AND t.periodo_evaluacion_id = ? ");
            params.add(periodoId);
        }
        if (materiaId != null) {
            sql.append("AND t.materia_id = ? ");
            params.add(materiaId);
        }
        if (soloPendientes) {
            sql.append("AND te.estatus_entrega = 'PENDIENTE' ");
        }

        sql.append("ORDER BY t.fecha_entrega DESC");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @GetMapping("/pendientes/grupo/{grupoId}")
    public ResponseEntity<List<Map<String, Object>>> pendientesDelGrupo(
            @PathVariable("grupoId") UUID grupoId,
            @RequestParam(value = "materia_id", required = false) UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT te.id, te.estudiante_id, te.estatus_entrega, " +
                "te.fecha_entrega, te.archivo_url, te.comentario_alumno, " +
                "t.titulo, t.tipo_item, t.fecha_entrega AS fecha_limite, " +
                "t.id AS actividad_id, " +
                "m.nombre_materia, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "est.matricula " +
                "FROM ades_tareas_entregas te " +
                "JOIN ades_tareas t ON t.id = te.tarea_id " +
                "JOIN ades_materias m ON m.id = t.materia_id " +
                "JOIN ades_estudiantes est ON est.id = te.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "WHERE t.grupo_id = ? AND te.estatus_entrega = 'ENTREGADA' AND te.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        params.add(grupoId);

        if (materiaId != null) {
            sql.append("AND t.materia_id = ? ");
            params.add(materiaId);
        }

        sql.append("ORDER BY t.fecha_entrega, p.apellido_paterno");

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> subirEntrega(
            @RequestParam("tarea_id") UUID tareaId,
            @RequestParam("alumno_id") UUID alumnoId,
            @RequestParam(value = "comentario", required = false) String comentario,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        String archivoUrl = null;
        if (archivo != null && !archivo.isEmpty()) {
            archivoUrl = minioService.uploadFile(tareaId, alumnoId, archivo);
        }

        jdbc.update(
                "INSERT INTO ades_tareas_entregas " +
                "(id, tarea_id, estudiante_id, fecha_entrega, comentario_alumno, archivo_url, estatus_entrega, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?, 'ENTREGADA', ?, ?) " +
                "ON CONFLICT (tarea_id, estudiante_id) DO UPDATE " +
                "SET fecha_entrega = CURRENT_TIMESTAMP, " +
                "    comentario_alumno = EXCLUDED.comentario_alumno, " +
                "    archivo_url = COALESCE(EXCLUDED.archivo_url, ades_tareas_entregas.archivo_url), " +
                "    estatus_entrega = 'ENTREGADA', " +
                "    fecha_modificacion = CURRENT_TIMESTAMP, " +
                "    row_version = ades_tareas_entregas.row_version + 1, " +
                "    usuario_modificacion = EXCLUDED.usuario_modificacion",
                UUID.randomUUID(), tareaId, alumnoId, comentario, archivoUrl, user.getUsername(), user.getUsername()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Entrega registrada");
        response.put("archivo_url", archivoUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{entregaId}/calificar")
    public ResponseEntity<Map<String, Object>> calificarEntrega(
            @PathVariable("entregaId") UUID entregaId,
            @RequestBody CalificarIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        int updated = jdbc.update(
                "UPDATE ades_tareas_entregas " +
                "SET calificacion_obtenida = ?, " +
                "    comentario_profesor = ?, " +
                "    calificado_por = ?, " +
                "    fecha_calificacion_docente = CURRENT_TIMESTAMP, " +
                "    estatus_entrega = 'CALIFICADA', " +
                "    fecha_modificacion = CURRENT_TIMESTAMP, " +
                "    row_version = row_version + 1, " +
                "    usuario_modificacion = ? " +
                "WHERE id = ? AND is_active = TRUE",
                body.getCalificacion(), body.getComentario(), user.getId(), user.getUsername(), entregaId
        );

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega no encontrada");
        }

        return ResponseEntity.ok(Map.of("message", "Calificación registrada"));
    }

    @PostMapping("/{entregaId}/excusa")
    public ResponseEntity<Map<String, Object>> registrarExcusa(
            @PathVariable("entregaId") UUID entregaId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        int updated = jdbc.update(
                "UPDATE ades_tareas_entregas " +
                "SET estatus_entrega = 'EXCUSA', " +
                "    comentario_profesor = ?, " +
                "    fecha_modificacion = CURRENT_TIMESTAMP, " +
                "    usuario_modificacion = ? " +
                "WHERE id = ?",
                motivo, user.getUsername(), entregaId
        );

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega no encontrada");
        }

        return ResponseEntity.ok(Map.of("message", "Excusa registrada"));
    }
}
