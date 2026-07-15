package mx.ades.modules.profesores;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.profesores.domain.port.in.ActualizarProfesorUseCase;
import mx.ades.modules.profesores.domain.port.in.CrearProfesorUseCase;
import mx.ades.modules.profesores.domain.port.out.ProfesorRepositoryPort;
import mx.ades.modules.profesores.query.ProfesorQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la gestión del personal docente.
 * Expone endpoints bajo /api/v1/profesores para listar profesores con filtros
 * (plantel, nivel, grado, grupo, búsqueda textual) con scoping automático por plantel,
 * obtener el detalle de un profesor, crear un nuevo registro docente, actualizar
 * datos parciales (persona + laborales) y reemplazar el registro completo.
 * Requiere JWT válido en los endpoints que modifican datos; el listado usa
 * {@code getEffectivePlantelId} para evitar acceso cross-plantel.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/profesores")
@RequiredArgsConstructor
public class ProfesorController {

    private final AdesUserService          userService;
    private final ProfesorQueryService     query;
    private final CrearProfesorUseCase     crear;
    private final ActualizarProfesorUseCase actualizar;
    private final ProfesorRepositoryPort   repositoryPort;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "nivel_id",   required = false) UUID nivelId,
            @RequestParam(name = "grado_id",   required = false) UUID gradoId,
            @RequestParam(name = "grupo_id",   required = false) UUID grupoId,
            @RequestParam(name = "buscar",     required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        return ResponseEntity.ok(query.listar(effectivePlantel, nivelId, gradoId, grupoId, buscar));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        // BOLA fix (auditoría 2026-07-15): el detalle incluye PII sensible del docente
        // (CURP, RFC, teléfono, email personal, fecha de nacimiento); antes no llamaba
        // a resolveUser() en absoluto — cualquier cuenta ADES autenticada (incl.
        // alumnos/padres) podía leer el expediente completo de cualquier profesor por id.
        AdesUser user = userService.resolveUser(jwt);
        Map<String, Object> profesor = query.obtener(id);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 && user.getPlantelId() != null) {
            Object plantelId = profesor.get("plantel_id");
            if (plantelId != null && !plantelId.equals(user.getPlantelId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede consultar un profesor de otro plantel");
            }
        }
        return ResponseEntity.ok(profesor);
    }

    @PostMapping
    public ResponseEntity<Profesor> create(@RequestBody Profesor req, @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        var cmd = new CrearProfesorUseCase.Command(
                req.getPersonaId(),
                req.getPlantelId(),
                req.getNumeroEmpleado(),
                req.getTipoContrato());
        return ResponseEntity.status(HttpStatus.CREATED).body(crear.crear(cmd));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");
        return ResponseEntity.ok(actualizar.actualizar(
                new ActualizarProfesorUseCase.Command(id, per, lab)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Profesor> update(
            @PathVariable UUID id,
            @RequestBody Profesor update,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        Profesor prof = repositoryPort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado"));
        prof.setNumeroEmpleado(update.getNumeroEmpleado());
        prof.setPersonaId(update.getPersonaId());
        prof.setPlantelId(update.getPlantelId());
        prof.setEstatusId(update.getEstatusId());
        prof.setTipoContrato(update.getTipoContrato());
        prof.setIsActive(update.getIsActive());
        return ResponseEntity.ok(repositoryPort.save(prof));
    }

    /**
     * Alta/edición del expediente docente es operación de personal escolar
     * (nivelAcceso &le;4). Padres/alumnos (nivelAcceso &gt;=5) no tienen razón
     * para modificar registros de personal — previene BFLA (OWASP API5).
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}
