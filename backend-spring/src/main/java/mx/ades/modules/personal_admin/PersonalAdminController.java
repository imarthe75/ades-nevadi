package mx.ades.modules.personal_admin;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.personal_admin.application.service.PersonalAdminApplicationService;
import mx.ades.modules.personal_admin.domain.port.in.RegistrarPersonalAdminUseCase;
import mx.ades.modules.personal_admin.query.PersonalAdminQueryService;
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
 * Adaptador REST para la gestión del personal no-docente y administrativo.
 * Expone endpoints bajo /api/v1/personal-admin para listar (con scoping automático
 * por plantel para no-admins), obtener detalle, registrar, actualizar y desactivar
 * empleados administrativos (directores, coordinadores, prefectos, etc.).
 * Requiere JWT válido en todos los endpoints; el listado usa
 * {@code getEffectivePlantelId} para evitar acceso cross-plantel.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/personal-admin")
@RequiredArgsConstructor
public class PersonalAdminController {

    private final AdesUserService userService;
    private final RegistrarPersonalAdminUseCase registrarPersonalAdminUseCase;
    private final PersonalAdminApplicationService personalAdminService;
    private final PersonalAdminQueryService queryService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "tipo_rol",   required = false) String tipoRol,
            @RequestParam(name = "buscar",     required = false) String buscar,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        UUID effectivePlantel = userService.getEffectivePlantelId(user, plantelId);
        List<Map<String, Object>> rows = queryService.listar(effectivePlantel, tipoRol, buscar);
        return ResponseEntity.ok(Map.of("data", rows, "total", rows.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        Map<String, Object> row;
        try {
            row = queryService.detalle(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        // BOLA fix: list() ya scopeaba por plantel vía getEffectivePlantelId, pero el detalle
        // por id no lo verificaba — un Director/Coordinador de un plantel podía ver el
        // expediente administrativo de personal de OTRO plantel con solo el UUID.
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 2 && user.getPlantelId() != null) {
            Object plantelRow = row.get("plantel_id");
            if (plantelRow != null && !user.getPlantelId().equals(plantelRow)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenece a su plantel");
            }
        }
        return ResponseEntity.ok(row);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        requireStaff(user);

        @SuppressWarnings("unchecked")
        Map<String, Object> per = (Map<String, Object>) body.get("persona");
        @SuppressWarnings("unchecked")
        Map<String, Object> lab = (Map<String, Object>) body.get("laborales");
        Object plantelIdObj = body.get("plantel_id");

        UUID plantelId;
        try {
            plantelId = UUID.fromString(plantelIdObj != null ? plantelIdObj.toString() : "");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "plantel_id es requerido");
        }

        RegistrarPersonalAdminUseCase.Command cmd;
        try {
            cmd = new RegistrarPersonalAdminUseCase.Command(plantelId, per, lab, user.getUsername());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        UUID empleadoId = registrarPersonalAdminUseCase.registrar(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.detalle(empleadoId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
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
            return ResponseEntity.ok(personalAdminService.actualizar(id, per, lab, user.getUsername()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        requireStaff(userService.resolveUser(jwt));
        try {
            personalAdminService.desactivar(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Alta/edición/baja de personal administrativo es operación de personal
     * escolar (nivelAcceso &le;4), igual que ProfesorController — padres/alumnos
     * (nivelAcceso &gt;=5) no tienen razón para crear o modificar registros de
     * personal — previene BFLA (OWASP API5).
     */
    private void requireStaff(AdesUser user) {
        Integer nivelAcceso = user.getNivelAcceso();
        if (nivelAcceso == null || nivelAcceso > 4) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nivel de acceso insuficiente para esta operación");
        }
    }
}
