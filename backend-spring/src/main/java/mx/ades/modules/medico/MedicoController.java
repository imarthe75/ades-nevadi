package mx.ades.modules.medico;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.medico.application.service.PersonalSaludApplicationService;
import mx.ades.modules.medico.domain.port.in.ActualizarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.in.RegistrarPersonalSaludUseCase;
import mx.ades.modules.medico.query.PersonalSaludQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
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

    // ══════════════════════════════════════════════════════════════════════════
    // EXPEDIENTES MÉDICOS (JPA)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/expedientes-medicos/alumno/{estudianteId}")
    public ResponseEntity<ExpedienteMedico> obtenerExpediente(@PathVariable("estudianteId") UUID estudianteId) {
        ExpedienteMedico exp = expedienteRepository.findByEstudianteId(estudianteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expediente médico no encontrado"));
        return ResponseEntity.ok(exp);
    }

    @PostMapping("/expedientes-medicos")
    public ResponseEntity<ExpedienteMedico> crearExpediente(@RequestBody ExpedienteMedico data) {
        expedienteRepository.findByEstudianteId(data.getEstudianteId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El alumno ya tiene expediente médico");
                });
        return ResponseEntity.status(HttpStatus.CREATED).body(expedienteRepository.save(data));
    }

    @PutMapping("/expedientes-medicos/{id}")
    public ResponseEntity<ExpedienteMedico> actualizarExpediente(
            @PathVariable("id") UUID id,
            @RequestBody ExpedienteMedico data) {

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
    public ResponseEntity<List<IncidenteMedico>> incidentesAlumno(@PathVariable("estudianteId") UUID estudianteId) {
        return ResponseEntity.ok(incidenteRepository.findByEstudianteIdAndIsActiveTrueOrderByFechaIncidenteDesc(estudianteId));
    }

    @PostMapping("/incidentes-medicos")
    public ResponseEntity<IncidenteMedico> registrarIncidente(@RequestBody IncidenteMedico data) {
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

        userService.resolveUser(jwt);
        Map<String, Object> row = personalSaludQueryService.detalle(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal de salud no encontrado");
        return ResponseEntity.ok(row);
    }

    @PostMapping("/personal-salud")
    public ResponseEntity<Map<String, Object>> createPersonalSalud(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

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

        userService.resolveUser(jwt);
        try {
            personalSaludService.desactivar(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
