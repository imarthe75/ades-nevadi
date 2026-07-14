package mx.ades.modules.medico;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.medico.application.service.SaludAvanzadaApplicationService;
import mx.ades.modules.medico.domain.port.in.*;
import mx.ades.modules.medico.query.SaludQueryService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para los módulos avanzados de salud escolar.
 * Expone endpoints bajo /api/v1/salud-avanzada en cuatro secciones:
 * <ul>
 *   <li>/medicamentos/{alumno_id} — registro y suspensión de medicamentos autorizados (SB-003);
 *       requiere {@code nivelAcceso} &le;3.</li>
 *   <li>/actas-incidente/{incidente_id} — generación de acta formal de incidente médico (SB-005).</li>
 *   <li>/psicosocial/{alumno_id} — historial y registro de sesiones psicosociales (SB-021);
 *       restringido a {@code nivelAcceso} &le;3.</li>
 *   <li>/tutorias — listado y registro de sesiones de tutoría (SB-022);
 *       restringido a {@code nivelAcceso} &le;3.</li>
 * </ul>
 * Proxea la generación de PDFs (actas, certificados deportivos) al microservicio FastAPI.
 * Requiere JWT válido en todos los endpoints.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/salud-avanzada")
@RequiredArgsConstructor
@Slf4j
public class SaludAvanzadaController {

    private final AdesUserService userService;
    private final SaludQueryService queryService;
    private final SaludAvanzadaApplicationService saludAvanzadaService;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/salud-avanzada";

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
        return ResponseEntity.ok(queryService.medicamentos(alumnoId, soloVigentes));
    }

    @PostMapping("/medicamentos/{alumno_id}")
    public ResponseEntity<Map<String, Object>> registrarMedicamento(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody MedicamentoPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        try {
            UUID id = saludAvanzadaService.registrar(new RegistrarMedicamentoUseCase.Command(
                alumnoId, data.getNombreMedicamento(), data.getDosis(), data.getFrecuencia(),
                data.getHorario(), data.getViaAdministracion(), data.getPrescritoPor(),
                parseDate(data.getFechaInicio()), parseDate(data.getFechaFin()),
                data.getObservaciones(), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Medicamento registrado"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @DeleteMapping("/medicamentos/{medicamento_id}")
    public ResponseEntity<Map<String, Object>> suspenderMedicamento(
            @PathVariable("medicamento_id") UUID medicamentoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        try {
            saludAvanzadaService.suspender(new SuspenderMedicamentoUseCase.Command(
                medicamentoId, user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.ok(Map.of("message", "Medicamento suspendido"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
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
        try {
            UUID id = saludAvanzadaService.generar(new GenerarActaIncidenteUseCase.Command(
                incidenteId, data.getDescripcionDetallada(), data.getTestigos(), data.getMedidasTomadas(),
                data.getRequirioTraslado(), data.getHospitalDestino(), data.getNotificadoFamilia(),
                data.getFirmaResponsable(), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Acta de incidente médico generada"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SB-021: Seguimiento Psicosocial
    // ──────────────────────────────────────────────────────────────────────────
    @GetMapping("/psicosocial/{alumno_id}")
    public ResponseEntity<List<Map<String, Object>>> historialPsicosocial(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        skip = Math.max(skip, 0);
        limit = Math.min(Math.max(limit, 1), 200);
        return ResponseEntity.ok(queryService.psicosocial(alumnoId, skip, limit));
    }

    @PostMapping("/psicosocial/{alumno_id}")
    public ResponseEntity<Map<String, Object>> registrarSesionPsicosocial(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody PsicosocialPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        try {
            UUID id = saludAvanzadaService.registrar(new RegistrarPsicosocialUseCase.Command(
                alumnoId, data.getTipoAtencion(), data.getMotivo(), data.getObservaciones(),
                data.getEstrategiasSugeridas(), data.getRequiereDerivacion(), data.getDerivadoA(),
                parseDate(data.getProximaSesion()), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Sesión psicosocial registrada"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
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
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return ResponseEntity.ok(queryService.tutorias(alumnoId, tipoTutoria, skip, limit));
    }

    @PostMapping("/tutorias")
    public ResponseEntity<Map<String, Object>> registrarTutoria(
            @RequestBody TutoriaPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        try {
            UUID id = saludAvanzadaService.registrar(new RegistrarTutoriaUseCase.Command(
                data.getAlumnoId(), data.getTipoTutoria(), data.getTema(), data.getDescripcion(),
                data.getDuracionMinutos(), data.getAcuerdos(), parseDate(data.getProximaSesion()),
                data.getRequiereSeguimiento(), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Sesión de tutoría registrada"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
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
        return proxyToPdf(API_BASE_URL + "/incidentes/" + incidenteId + "/acta-pdf", authHeader);
    }

    @GetMapping("/certificado-deportivo/{alumno_id}")
    public ResponseEntity<byte[]> descargarCertificadoDeportivo(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return proxyToPdf(API_BASE_URL + "/certificado-deportivo/" + alumnoId, authHeader);
    }

    private ResponseEntity<byte[]> proxyToPdf(String url, String authHeader) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(url);
            if (authHeader != null) request.header(HttpHeaders.AUTHORIZATION, authHeader);
            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (cd != null) headers.set(HttpHeaders.CONTENT_DISPOSITION, cd);
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // Preserva el status real de FastAPI (ej. 404 "no encontrado") en vez
            // de colapsarlo siempre a 502 Bad Gateway.
            throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar PDF: " + e.getMessage());
        }
    }

    private LocalDate parseDate(String value) {
        return value != null ? LocalDate.parse(value) : null;
    }
}
