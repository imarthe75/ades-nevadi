package mx.ades.modules.medico;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ades.modules.medico.application.service.SaludAvanzadaApplicationService;
import mx.ades.modules.medico.domain.port.in.*;
import mx.ades.modules.medico.query.SaludQueryService;
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
    private final JdbcTemplate jdbc;
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
        AdesUser user = userService.resolveUser(jwt);
        // Inconsistente con historialPsicosocial/listarTutorias (mismo controller), que sí
        // validan nivelAcceso <=3 — sin este chequeo cualquier cuenta autenticada podía leer
        // los medicamentos autorizados de cualquier alumno (dato de salud sensible, LFPDPPP).
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        verificarAccesoAlumno(user, alumnoId);
        return ResponseEntity.ok(queryService.medicamentos(alumnoId, soloVigentes));
    }

    @PostMapping("/medicamentos/{alumno_id}")
    public ResponseEntity<Map<String, Object>> registrarMedicamento(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestBody MedicamentoPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAlumno(user, alumnoId);
        try {
            UUID id = saludAvanzadaService.registrar(new RegistrarMedicamentoUseCase.Command(
                alumnoId, data.getNombreMedicamento(), data.getDosis(), data.getFrecuencia(),
                data.getHorario(), data.getViaAdministracion(), data.getPrescritoPor(),
                parseDate(data.getFechaInicio()), parseDate(data.getFechaFin()),
                data.getObservaciones(), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Medicamento registrado"));
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
        }
    }

    @DeleteMapping("/medicamentos/{medicamento_id}")
    public ResponseEntity<Map<String, Object>> suspenderMedicamento(
            @PathVariable("medicamento_id") UUID medicamentoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        verificarAccesoAlumno(user, alumnoDeMedicamento(medicamentoId));
        try {
            saludAvanzadaService.suspender(new SuspenderMedicamentoUseCase.Command(
                medicamentoId, user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.ok(Map.of("message", "Medicamento suspendido"));
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
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
        verificarAccesoAlumno(user, alumnoDeIncidente(incidenteId));
        try {
            UUID id = saludAvanzadaService.generar(new GenerarActaIncidenteUseCase.Command(
                incidenteId, data.getDescripcionDetallada(), data.getTestigos(), data.getMedidasTomadas(),
                data.getRequirioTraslado(), data.getHospitalDestino(), data.getNotificadoFamilia(),
                data.getFirmaResponsable(), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Acta de incidente médico generada"));
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
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
        verificarAccesoAlumno(user, alumnoId);
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
        verificarAccesoAlumno(user, alumnoId);
        try {
            UUID id = saludAvanzadaService.registrar(new RegistrarPsicosocialUseCase.Command(
                alumnoId, data.getTipoAtencion(), data.getMotivo(), data.getObservaciones(),
                data.getEstrategiasSugeridas(), data.getRequiereDerivacion(), data.getDerivadoA(),
                parseDate(data.getProximaSesion()), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Sesión psicosocial registrada"));
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
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
        if (alumnoId != null) {
            verificarAccesoAlumno(user, alumnoId);
            return ResponseEntity.ok(queryService.tutorias(alumnoId, tipoTutoria, null, skip, limit));
        }
        UUID plantelId = userService.getEffectivePlantelId(user, null);
        return ResponseEntity.ok(queryService.tutorias(null, tipoTutoria, plantelId, skip, limit));
    }

    @PostMapping("/tutorias")
    public ResponseEntity<Map<String, Object>> registrarTutoria(
            @RequestBody TutoriaPayload data,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (data.getAlumnoId() != null) verificarAccesoAlumno(user, data.getAlumnoId());
        try {
            UUID id = saludAvanzadaService.registrar(new RegistrarTutoriaUseCase.Command(
                data.getAlumnoId(), data.getTipoTutoria(), data.getTema(), data.getDescripcion(),
                data.getDuracionMinutos(), data.getAcuerdos(), parseDate(data.getProximaSesion()),
                data.getRequiereSeguimiento(), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id.toString(), "message", "Sesión de tutoría registrada"));
        } catch (IllegalArgumentException e) {
            throw mapIllegalArgument(e);
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
        // El acta de incidente médico es un documento de salud sensible (solo se
        // consume desde el módulo de personal médico/coordinación, ver medico.component.ts);
        // faltaba el mismo nivelAcceso <=3 que exige generarActaIncidente().
        AdesUser user = userService.resolveUser(jwt);
        requireStaffMedico(user);
        verificarAccesoAlumno(user, alumnoDeIncidente(incidenteId));
        return proxyToPdf(API_BASE_URL + "/incidentes/" + incidenteId + "/acta-pdf", authHeader);
    }

    @GetMapping("/certificado-deportivo/{alumno_id}")
    public ResponseEntity<byte[]> descargarCertificadoDeportivo(
            @PathVariable("alumno_id") UUID alumnoId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        // Igual que descargarActaIncidente: sin este chequeo, cualquier cuenta
        // autenticada podía descargar el certificado deportivo (datos de salud) de
        // cualquier alumno solo conociendo su UUID.
        AdesUser user = userService.resolveUser(jwt);
        requireStaffMedico(user);
        verificarAccesoAlumno(user, alumnoId);
        return proxyToPdf(API_BASE_URL + "/certificado-deportivo/" + alumnoId, authHeader);
    }

    /**
     * Documentos de salud (actas de incidente, certificados deportivos) — solo
     * personal médico/coordinación (nivelAcceso &le;3), igual que las escrituras
     * de este módulo (medicamentos, psicosocial, tutorías).
     */
    private void requireStaffMedico(AdesUser user) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
    }

    /**
     * BOLA fix (2026-07-16): este módulo solo verificaba nivelAcceso — sin ningún
     * chequeo de plantel, personal médico/coordinación de un plantel podía leer o
     * escribir medicamentos, incidentes, seguimiento psicosocial y tutorías de
     * CUALQUIER plantel (mismo patrón ya confirmado real en CondicionCronicaController).
     * Resuelve el plantel del alumno y delega en el umbral central de
     * {@code AdesUserService#verificarPlantel} (solo nivelAcceso 0 = alcance libre).
     */
    private void verificarAccesoAlumno(AdesUser user, UUID alumnoId) {
        List<UUID> plantelRows = jdbc.queryForList(
                "SELECT plantel_id FROM ades_estudiantes WHERE id = ?", UUID.class, alumnoId);
        if (plantelRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        userService.verificarPlantel(user, plantelRows.get(0), "El alumno no pertenece a su plantel");
    }

    private UUID alumnoDeMedicamento(UUID medicamentoId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT alumno_id FROM ades_medicamentos_alumno WHERE id = ?", UUID.class, medicamentoId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado");
        return rows.get(0);
    }

    private UUID alumnoDeIncidente(UUID incidenteId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT estudiante_id FROM ades_incidentes_medicos WHERE id = ?", UUID.class, incidenteId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incidente médico no encontrado");
        return rows.get(0);
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

    /**
     * Los compact constructors de los records {@code Command} de este módulo lanzan
     * {@code IllegalArgumentException} tanto para el chequeo de {@code nivelAcceso}
     * (autorización, 403) como para campos NOT NULL faltantes (validación, 422) —
     * antes de este helper ambos casos colapsaban siempre a 403 Forbidden, lo cual
     * era engañoso para un usuario con acceso suficiente que solo olvidó un campo.
     */
    private ResponseStatusException mapIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage();
        HttpStatus status = (msg != null && msg.startsWith("Se requiere nivel"))
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return new ResponseStatusException(status, msg);
    }
}
