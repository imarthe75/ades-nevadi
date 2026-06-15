package mx.ades.modules.justificaciones;

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

import java.util.*;

@RestController
@RequestMapping("/api/v1/justificaciones")
@RequiredArgsConstructor
public class JustificacionController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;

    private static final Set<String> TIPOS = new HashSet<>(Arrays.asList(
            "MEDICA", "FAMILIAR", "DEPORTIVA", "CULTURAL", "ADMINISTRATIVA", "OTRA"
    ));

    @Data
    public static class JustificacionCreate {
        private UUID asistenciaId;
        private String tipoJustificacion = "MEDICA";
        private String motivo;
        private String documentoUrl;
    }

    @Data
    public static class ResolucionIn {
        private String accion;
        private String motivoRechazo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarJustificaciones(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "grupo_id", required = false) UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder query = new StringBuilder(
                "SELECT j.id, j.asistencia_id, j.tipo_justificacion, j.motivo, " +
                "j.documento_url, j.estado, j.motivo_rechazo, j.fecha_resolucion, " +
                "j.fecha_creacion, " +
                "a.estudiante_id, " +
                "p.nombre || ' ' || p.apellido_paterno AS alumno_nombre, " +
                "a.estatus_asistencia, " +
                "cl.fecha_clase " +
                "FROM ades_justificaciones_falta j " +
                "JOIN ades_asistencias a  ON a.id = j.asistencia_id " +
                "JOIN ades_estudiantes e  ON e.id = a.estudiante_id " +
                "JOIN ades_personas p     ON p.id = e.persona_id " +
                "LEFT JOIN ades_clases cl ON cl.id = a.clase_id " +
                "WHERE j.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (estudianteId != null) {
            query.append("AND a.estudiante_id = ? ");
            params.add(estudianteId);
        }
        if (estado != null && !estado.isBlank()) {
            query.append("AND j.estado = ? ");
            params.add(estado.toUpperCase());
        }
        if (grupoId != null) {
            query.append("AND cl.grupo_id = ? ");
            params.add(grupoId);
        }

        query.append("ORDER BY j.fecha_creacion DESC");

        return ResponseEntity.ok(jdbc.queryForList(query.toString(), params.toArray()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crearJustificacion(
            @RequestBody JustificacionCreate body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        String tipo = body.getTipoJustificacion() != null ? body.getTipoJustificacion().toUpperCase() : "MEDICA";
        if (!TIPOS.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "tipo_justificacion inválido");
        }

        List<Map<String, Object>> asist = jdbc.queryForList(
                "SELECT id, estatus_asistencia FROM ades_asistencias WHERE id = ? AND is_active = TRUE",
                body.getAsistenciaId()
        );
        if (asist.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asistencia no encontrada");
        }
        String estatusAsistencia = (String) asist.get(0).get("estatus_asistencia");
        if ("PRESENTE".equalsIgnoreCase(estatusAsistencia)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede justificar una asistencia PRESENTE");
        }

        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM ades_justificaciones_falta WHERE asistencia_id = ? AND is_active = TRUE",
                body.getAsistenciaId()
        );
        if (!existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una justificación para esta asistencia");
        }

        UUID newId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_justificaciones_falta " +
                "(id, asistencia_id, tipo_justificacion, motivo, documento_url, estado, usuario_creacion, usuario_modificacion) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDIENTE', ?, ?)",
                newId, body.getAsistenciaId(), tipo, body.getMotivo(), body.getDocumentoUrl(),
                user.getId().toString(), user.getId().toString()
        );

        jdbc.update(
                "UPDATE ades_asistencias SET justificacion_id = ? WHERE id = ?",
                newId, body.getAsistenciaId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", newId.toString()));
    }

    @PostMapping("/{justificacionId}/resolver")
    public ResponseEntity<Map<String, Object>> resolverJustificacion(
            @PathVariable("justificacionId") UUID justificacionId,
            @RequestBody ResolucionIn body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        if (user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin permisos para resolver justificaciones");
        }

        List<Map<String, Object>> just = jdbc.queryForList(
                "SELECT id, estado FROM ades_justificaciones_falta WHERE id = ? AND is_active = TRUE",
                justificacionId
        );
        if (just.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Justificación no encontrada");
        }
        String estadoActual = (String) just.get(0).get("estado");
        if (!"PENDIENTE".equalsIgnoreCase(estadoActual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La justificación ya fue resuelta: " + estadoActual);
        }

        String accion = body.getAccion() != null ? body.getAccion().toUpperCase() : "";
        if (!"APROBAR".equals(accion) && !"RECHAZAR".equals(accion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción debe ser APROBAR o RECHAZAR");
        }

        if ("RECHAZAR".equals(accion) && (body.getMotivoRechazo() == null || body.getMotivoRechazo().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "motivo_rechazo es obligatorio al rechazar");
        }

        String nuevoEstado = "APROBAR".equals(accion) ? "APROBADA" : "RECHAZADA";

        jdbc.update(
                "UPDATE ades_justificaciones_falta " +
                "SET estado = ?, aprobada_por = ?, fecha_resolucion = CURRENT_TIMESTAMP, " +
                "motivo_rechazo = ?, usuario_modificacion = ? " +
                "WHERE id = ?",
                nuevoEstado, user.getId(), body.getMotivoRechazo(), user.getId().toString(), justificacionId
        );

        return ResponseEntity.ok(Map.of("estado", nuevoEstado));
    }

    @GetMapping("/{justificacionId}")
    public ResponseEntity<Map<String, Object>> obtenerJustificacion(
            @PathVariable("justificacionId") UUID justificacionId,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ades_justificaciones_falta WHERE id = ? AND is_active = TRUE",
                justificacionId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Justificación no encontrada");
        }
        return ResponseEntity.ok(rows.get(0));
    }
}
