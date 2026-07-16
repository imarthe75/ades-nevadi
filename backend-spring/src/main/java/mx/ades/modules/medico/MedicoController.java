package mx.ades.modules.medico;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.medico.application.service.PersonalSaludApplicationService;
import mx.ades.modules.medico.domain.port.in.ActualizarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.in.RegistrarPersonalSaludUseCase;
import mx.ades.modules.medico.query.PersonalSaludQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Adaptador REST para expedientes médicos, incidentes y personal de salud.
 * Expone endpoints bajo /api/v1 en tres secciones:
 * <ul>
 *   <li>/expedientes-medicos — CRUD de expediente médico por alumno (tipo sangre, alergias,
 *       condiciones crónicas, discapacidad, seguro médico, NSS, vacunas).</li>
 *   <li>/incidentes-medicos — registro y consulta de incidentes médicos por alumno.</li>
 *   <li>/personal-salud — alta, consulta, actualización y baja del personal de enfermería/salud
 *       con scoping por plantel para no-admins (evita lectura cross-plantel).</li>
 * </ul>
 * Los endpoints de personal de salud requieren JWT válido; expedientes e incidentes usan JPA directa.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MedicoController {

    private final ExpedienteMedicoRepository expedienteRepository;
    private final IncidenteMedicoRepository incidenteRepository;
    private final AdesUserService userService;
    private final RegistrarPersonalSaludUseCase registrarPersonalSaludUseCase;
    private final ActualizarPersonalSaludUseCase actualizarPersonalSaludUseCase;
    private final PersonalSaludApplicationService personalSaludService;
    private final PersonalSaludQueryService personalSaludQueryService;

    /**
     * Expedientes e incidentes médicos son datos de salud sensibles (LFPDPPP).
     * Solo personal escolar (nivelAcceso &le;4: admin/director/coordinador/docente/
     * enfermería) puede registrar o modificar estos registros — previene BFLA
     * (OWASP API5) sobre datos de salud de menores.
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPEDIENTES MÉDICOS (JPA)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/expedientes-medicos/alumno/{estudianteId}")
    public ResponseEntity<ExpedienteMedico> obtenerExpediente(
            @PathVariable("estudianteId") UUID estudianteId,
            @AuthenticationPrincipal Jwt jwt) {
        // Sin resolveUser() aquí, este endpoint quedaba accesible a cualquier cuenta
        // autenticada del sistema (incluyendo padres/alumnos de otro expediente) sin
        // ninguna verificación de rol — exponía datos de salud de menores (LFPDPPP).
        requireStaff(userService.resolveUser(jwt));
        ExpedienteMedico exp = expedienteRepository.findByEstudianteId(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente médico no encontrado"));
        return ResponseEntity.ok(exp);
    }

    @PostMapping("/expedientes-medicos")
    public ResponseEntity<ExpedienteMedico> crearExpediente(
            @RequestBody ExpedienteMedico data,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        if (data.getEstudianteId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "estudianteId es obligatorio");
        }
        expedienteRepository.findByEstudianteId(data.getEstudianteId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El alumno ya tiene expediente médico");
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(expedienteRepository.save(data));
    }

    @PutMapping("/expedientes-medicos/{id}")
    public ResponseEntity<ExpedienteMedico> actualizarExpediente(
            @PathVariable("id") UUID id,
            @RequestBody ExpedienteMedico data,
            @AuthenticationPrincipal Jwt jwt) {

        requireStaff(userService.resolveUser(jwt));
        ExpedienteMedico exp = expedienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente no encontrado"));

        if (data.getTipoSangre() != null) exp.setTipoSangre(data.getTipoSangre());
        if (data.getAlergias() != null) exp.setAlergias(data.getAlergias());
        if (data.getMedicamentosAutorizados() != null) exp.setMedicamentosAutorizados(data.getMedicamentosAutorizados());
        if (data.getCondicionesCronicas() != null) exp.setCondicionesCronicas(data.getCondicionesCronicas());
        if (data.getObservacionesGenerales() != null) exp.setObservacionesGenerales(data.getObservacionesGenerales());
        if (data.getNss() != null) exp.setNss(data.getNss());
        if (data.getDiscapacidad() != null) exp.setDiscapacidad(data.getDiscapacidad());
        if (data.getSeguroMedicoTipo() != null) exp.setSeguroMedicoTipo(data.getSeguroMedicoTipo());
        if (data.getSeguroMedicoNumero() != null) exp.setSeguroMedicoNumero(data.getSeguroMedicoNumero());
        if (data.getVacunasAlDia() != null) exp.setVacunasAlDia(data.getVacunasAlDia());
        if (data.getPadecimientoCronico() != null) exp.setPadecimientoCronico(data.getPadecimientoCronico());
        if (data.getRequiereMedicacion() != null) exp.setRequiereMedicacion(data.getRequiereMedicacion());

        return ResponseEntity.ok(expedienteRepository.save(exp));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INCIDENTES MÉDICOS (JPA)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/incidentes-medicos/alumno/{estudianteId}")
    public ResponseEntity<List<IncidenteMedico>> incidentesAlumno(
            @PathVariable("estudianteId") UUID estudianteId,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "20") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        // Igual que obtenerExpediente(): faltaba resolveUser(), dejando los incidentes
        // médicos de cualquier alumno accesibles a cualquier cuenta autenticada.
        requireStaff(userService.resolveUser(jwt));
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(incidenteRepository.findByEstudianteIdAndIsActiveTrueOrderByFechaIncidenteDesc(
                estudianteId, PageRequest.of(pagina - 1, porPagina)));
    }

    @PostMapping("/incidentes-medicos")
    public ResponseEntity<IncidenteMedico> registrarIncidente(
            @RequestBody IncidenteMedico data,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        // estudiante_id y descripcion son NOT NULL en ades_incidentes_medicos (sin default).
        // @Column(nullable=false) es solo metadata DDL: sin este chequeo explícito, un
        // payload incompleto llegaba hasta el flush de Hibernate y salía como
        // DataIntegrityViolationException -> 409 genérico en vez de un 422 claro.
        if (data.getEstudianteId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "estudianteId es obligatorio");
        }
        if (data.getDescripcion() == null || data.getDescripcion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "descripcion es obligatoria");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(incidenteRepository.save(data));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PERSONAL DE SALUD (hexagonal)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/personal-salud")
    public ResponseEntity<Map<String, Object>> listPersonalSalud(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "buscar",     required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(Map.of("data", personalSaludQueryService.listar(effectivePlantel, buscar)));
    }

    @GetMapping("/personal-salud/{id}")
    public ResponseEntity<Map<String, Object>> getPersonalSalud(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        Map<String, Object> row = personalSaludQueryService.detalle(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal de salud no encontrado");
        // BOLA fix: listPersonalSalud() ya scopea por plantel vía getEffectivePlantelId, pero
        // el detalle por id no lo verificaba — cualquier usuario podía ver el expediente
        // (CURP, teléfono, email personal) de personal de salud de OTRO plantel por UUID.
        // (Corregido 2026-07-16 — decisión explícita del usuario: el umbral original `> 2`
        // dejaba a Director y Admin_Plantel sin restricción; ver
        // AdesUserService#getEffectivePlantelId.)
        userService.verificarPlantel(user, (UUID) row.get("plantel_id"), "No pertenece a su plantel");
        return ResponseEntity.ok(row);
    }

    @PostMapping("/personal-salud")
    public ResponseEntity<Map<String, Object>> createPersonalSalud(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        // Alta de personal de salud es igual de sensible que expedientes/incidentes
        // (mismo requireStaff que el resto del controller); sin este chequeo cualquier
        // cuenta autenticada, incl. padres/alumnos, podía registrar personal escolar.
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);

        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");
        Object plantelIdObj = body.get("plantel_id");

        if (plantelIdObj == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es requerido");

        try {
            UUID saludId = registrarPersonalSaludUseCase.registrar(
                new RegistrarPersonalSaludUseCase.Command(
                    UUID.fromString(plantelIdObj.toString()), per, lab, user.getUsername()));
            return ResponseEntity.status(HttpStatus.CREATED).body(personalSaludService.detalle(saludId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @PatchMapping("/personal-salud/{id}")
    public ResponseEntity<Map<String, Object>> patchPersonalSalud(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);

        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");

        try {
            actualizarPersonalSaludUseCase.actualizar(
                new ActualizarPersonalSaludUseCase.Command(id, per, lab, user.getUsername()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return ResponseEntity.ok(personalSaludService.detalle(id));
    }

    @DeleteMapping("/personal-salud/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivatePersonalSalud(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        requireStaff(userService.resolveUser(jwt));
        try {
            personalSaludService.desactivar(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
