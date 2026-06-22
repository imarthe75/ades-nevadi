package mx.ades.modules.acta;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.acta.query.ActaQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acta de evaluación UAEMEX (preparatoria incorporada).
 * Documento por grupo×materia — solo lectura, roleGuard ≤ 3.
 */
@RestController
@RequestMapping("/api/v1/reportes/acta")
@RequiredArgsConstructor
public class ActaController {

    private final AdesUserService  userService;
    private final ActaQueryService query;

    /** Grupos UAEMEX vigentes para el selector. */
    @GetMapping("/grupos")
    public ResponseEntity<List<Map<String, Object>>> grupos(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            return ResponseEntity.status(403).build();
        }
        UUID effectivePlantel = plantelId != null ? plantelId
                : (user.getNivelAcceso() != null && user.getNivelAcceso() > 1 ? user.getPlantelId() : null);
        return ResponseEntity.ok(query.gruposUaemex(effectivePlantel));
    }

    /** Materias que se imparten en el grupo. */
    @GetMapping("/grupos/{grupo_id}/materias")
    public ResponseEntity<List<Map<String, Object>>> materias(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(query.materiasGrupo(grupoId));
    }

    /** Periodos FINAL / EXTRAORDINARIO del ciclo del grupo. */
    @GetMapping("/grupos/{grupo_id}/periodos")
    public ResponseEntity<List<Map<String, Object>>> periodos(
            @PathVariable("grupo_id") UUID grupoId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(query.periodosGrupo(grupoId));
    }

    /** Acta completa: cabecera + lista de alumnos + estadísticas. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> acta(
            @RequestParam("grupo_id")   UUID grupoId,
            @RequestParam("materia_id") UUID materiaId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            return ResponseEntity.status(403).build();
        }
        Map<String, Object> result = query.acta(grupoId, materiaId);
        if (result.containsKey("error")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
