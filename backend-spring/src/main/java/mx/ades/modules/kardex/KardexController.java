package mx.ades.modules.kardex;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.kardex.query.KardexQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kardex / historial académico UAEMEX (preparatoria incorporada). Solo lectura.
 */
@RestController
@RequestMapping("/api/v1/reportes/kardex")
@RequiredArgsConstructor
public class KardexController {

    private final AdesUserService    userService;
    private final KardexQueryService query;

    /** Grupos UAEMEX vigentes — para el cascading LOV del kardex. */
    @GetMapping("/grupos")
    public ResponseEntity<List<Map<String, Object>>> grupos(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        UUID scope = plantelId;
        if (scope == null && user.getNivelAcceso() != null && user.getNivelAcceso() > 1) {
            scope = user.getPlantelId();
        }
        return ResponseEntity.ok(query.gruposUaemex(scope));
    }

    /** Alumnos del grupo — para el cascading LOV del kardex. */
    @GetMapping("/grupos/{grupo_id}/alumnos")
    public ResponseEntity<List<Map<String, Object>>> alumnosGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }
        return ResponseEntity.ok(query.alumnosGrupo(grupoId));
    }

    @GetMapping("/{estudiante_id}")
    public ResponseEntity<Map<String, Object>> kardex(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo coordinación/dirección puede consultar el kardex");
        }
        Map<String, Object> kardex = query.kardex(estudianteId, cicloId);
        if (kardex.get("alumno") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sin kardex UAEMEX para el estudiante/ciclo indicado");
        }
        return ResponseEntity.ok(kardex);
    }
}
