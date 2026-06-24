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
        userService.resolveUser(jwt);
        try {
            return ResponseEntity.ok(queryService.detalle(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

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
        userService.resolveUser(jwt);
        try {
            personalAdminService.desactivar(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
