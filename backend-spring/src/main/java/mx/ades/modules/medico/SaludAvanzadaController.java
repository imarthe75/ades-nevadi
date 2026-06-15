package mx.ades.modules.medico;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/salud-avanzada")
@RequiredArgsConstructor
@Slf4j
public class SaludAvanzadaController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/salud-avanzada";
    private static final int NIVEL_MEDICO = 3;

    @Data
    public static class MedicamentoPayload {
        private String nombreMedicamento;
        private String dosis;
        private String frecuencia;
        private String horario;
        private String viaAdministracion = "ORAL";
        private String prescritoPor;
        private String fechaInicio;
        private String fechaFin;
        private String observaciones;
    }

    @Data
    public static class ActaIncidentePayload {
        private String descripcionDetallada;
        private String testigos;
        private String medidasTomadas;
        private Boolean requirioTraslado = false;
        private String hospitalDestino;
        private Boolean notificadoFamilia = true;
        private String firmaResponsable;
    }

    @Data
    public static class PsicosocialPayload {
        private String tipoAtencion;
        private String motivo;
        private String observaciones;
        private String estrategiasSugeridas;
        private Boolean requiereDerivacion = false;
        private String derivadoA;
        private String proximaSesion;
    }

    @Data
    public static class TutoriaPayload {
        private UUID alumnoId;
        private String tipoTutoria;
        private String tema;
        private String descripcion;
        private Integer duracionMinutos = 50;
        private String acuerdos;
        private String proximaSesion;
        private Boolean requiereSeguimiento = false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SB-003: Medicamentos
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/medicamentos/{alumno_id}")
    public ResponseEntity<List<Map<String, Object>>> listarMedicamentos(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestParam(value = "solo_vigentes", defaultValue = "true") boolean soloVigentes,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder("SELECT id, nombre_medicamento, dosis, frecuencia, horario, via_administracion, prescrito_por, fecha_inicio, fecha_fin, observaciones, is_active FROM ades_medicamentos_alumno WHERE alumno_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(alumnoId);

        if (soloVigentes) {
            sql.append(" AND is_active = TRUE AND (fecha_fin IS NULL OR fecha_fin >= CURRENT_DATE)");
        }

        sql.append(" ORDER BY fecha_inicio DESC NULLS LAST");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/medicamentos/{alumno_id}")
    public ResponseEntity<Map<String, Object>> registrarMedicamento(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody MedicamentoPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MEDICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere nivel Médico/Coordinador o superior");
        }

        UUID id = UUID.randomUUID();
        LocalDate fi = data.getFechaInicio() != null ? LocalDate.parse(data.getFechaInicio()) : null;
        LocalDate ff = data.getFechaFin() != null ? LocalDate.parse(data.getFechaFin()) : null;

        jdbc.update(
                "INSERT INTO ades_medicamentos_alumno " +
                        "(id, alumno_id, nombre_medicamento, dosis, frecuencia, horario, " +
                        "via_administracion, prescrito_por, fecha_inicio, fecha_fin, " +
                        "observaciones, usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, alumnoId, data.getNombreMedicamento(), data.getDosis(), data.getFrecuencia(), data.getHorario(),
                data.getViaAdministracion(), data.getPrescritoPor(), fi, ff,
                data.getObservaciones(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Medicamento registrado"));
    }

    @DeleteMapping("/medicamentos/{medicamento_id}")
    public ResponseEntity<Map<String, Object>> suspenderMedicamento(
            @PathVariable("medicamento_id") UUID medicamentoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MEDICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        jdbc.update(
                "UPDATE ades_medicamentos_alumno SET is_active = FALSE, usuario_modificacion = ? WHERE id = ?",
                user.getUsername(), medicamentoId
        );

        return ResponseEntity.ok(Map.of("message", "Medicamento suspendido"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SB-005: Acta de Incidente Médico
    // ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/actas-incidente/{incidente_id}")
    public ResponseEntity<Map<String, Object>> generarActaIncidente(
            @PathVariable("incidente_id") UUID incidenteId,
            @RequestBody ActaIncidentePayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MEDICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        // Verify incident exists
        List<Map<String, Object>> inc = jdbc.queryForList(
                "SELECT id FROM ades_incidentes_medicos WHERE id = ?", incidenteId);
        if (inc.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incidente médico no encontrado");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ades_actas_incidente_medico " +
                        "(id, incidente_id, descripcion_detallada, testigos, medidas_tomadas, " +
                        "requirio_traslado, hospital_destino, notificado_familia, " +
                        "firma_responsable, usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, incidenteId, data.getDescripcionDetallada(), data.getTestigos(), data.getMedidasTomadas(),
                data.getRequirioTraslado(), data.getHospitalDestino(), data.getNotificadoFamilia(),
                data.getFirmaResponsable(), user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Acta de incidente médico generada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SB-021: Seguimiento Psicosocial
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/psicosocial/{alumno_id}")
    public ResponseEntity<List<Map<String, Object>>> historialPsicosocial(
            @PathVariable("alumno_id") UUID alumnoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MEDICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tipo_atencion, motivo, observaciones, estrategias_sugeridas, " +
                        "requiere_derivacion, derivado_a, proxima_sesion, " +
                        "fecha_creacion, usuario_creacion as especialista " +
                        "FROM ades_seguimiento_psicosocial " +
                        "WHERE alumno_id = ? " +
                        "ORDER BY fecha_creacion DESC",
                alumnoId
        );
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/psicosocial/{alumno_id}")
    public ResponseEntity<Map<String, Object>> registrarSesionPsicosocial(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody PsicosocialPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MEDICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        UUID id = UUID.randomUUID();
        LocalDate proxima = data.getProximaSesion() != null ? LocalDate.parse(data.getProximaSesion()) : null;

        jdbc.update(
                "INSERT INTO ades_seguimiento_psicosocial " +
                        "(id, alumno_id, tipo_atencion, motivo, observaciones, estrategias_sugeridas, " +
                        "requiere_derivacion, derivado_a, proxima_sesion, " +
                        "usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, alumnoId, data.getTipoAtencion(), data.getMotivo(), data.getObservaciones(), data.getEstrategiasSugeridas(),
                data.getRequiereDerivacion(), data.getDerivadoA(), proxima,
                user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Sesión psicosocial registrada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SB-022: Tutoría
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/tutorias")
    public ResponseEntity<List<Map<String, Object>>> listarTutorias(
            @RequestParam(value = "alumno_id", required = false) UUID alumnoId,
            @RequestParam(value = "tipo_tutoria", required = false) String tipoTutoria,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MEDICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.tipo_tutoria, t.tema, t.descripcion, t.duracion_minutos, t.acuerdos, t.proxima_sesion, t.requiere_seguimiento, " +
                        "t.fecha_creacion, t.usuario_creacion as tutor, p.nombre || ' ' || p.apellido_paterno as alumno, e.matricula as numero_control " +
                        "FROM ades_tutorias t " +
                        "JOIN ades_estudiantes e ON e.id = t.alumno_id " +
                        "JOIN ades_personas p ON p.id = e.persona_id " +
                        "WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (alumnoId != null) {
            sql.append(" AND t.alumno_id = ?");
            params.add(alumnoId);
        }
        if (tipoTutoria != null && !tipoTutoria.isBlank()) {
            sql.append(" AND t.tipo_tutoria = ?");
            params.add(tipoTutoria);
        }

        sql.append(" ORDER BY t.fecha_creacion DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(skip);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/tutorias")
    public ResponseEntity<Map<String, Object>> registrarTutoria(
            @RequestBody TutoriaPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MEDICO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        UUID id = UUID.randomUUID();
        LocalDate proxima = data.getProximaSesion() != null ? LocalDate.parse(data.getProximaSesion()) : null;

        jdbc.update(
                "INSERT INTO ades_tutorias " +
                        "(id, alumno_id, tipo_tutoria, tema, descripcion, duracion_minutos, " +
                        "acuerdos, proxima_sesion, requiere_seguimiento, " +
                        "usuario_creacion, usuario_modificacion) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, data.getAlumnoId(), data.getTipoTutoria(), data.getTema(), data.getDescripcion(), data.getDuracionMinutos(),
                data.getAcuerdos(), proxima, data.getRequiereSeguimiento(),
                user.getUsername(), user.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Sesión de tutoría registrada"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PDF Generators Proxy
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/incidentes/{incidente_id}/acta-pdf")
    public ResponseEntity<byte[]> descargarActaIncidente(
            @PathVariable("incidente_id") UUID incidenteId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(API_BASE_URL + "/incidentes/" + incidenteId + "/acta-pdf");
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            if (response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION) != null) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            }
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar acta PDF en microservicio FastAPI: " + e.getMessage());
        }
    }

    @GetMapping("/certificado-deportivo/{alumno_id}")
    public ResponseEntity<byte[]> descargarCertificadoDeportivo(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(API_BASE_URL + "/certificado-deportivo/" + alumnoId);
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            if (response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION) != null) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            }
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar certificado deportivo en microservicio FastAPI: " + e.getMessage());
        }
    }
}
