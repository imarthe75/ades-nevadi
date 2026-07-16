package mx.ades.modules.acta;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.acta.query.ActaQueryService;
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
 * Acta de evaluación UAEMEX (preparatoria incorporada).
 * Documento por grupo×materia — solo lectura, roleGuard ≤ 3.
 */
@RestController
@RequestMapping("/api/v1/reportes/acta")
@RequiredArgsConstructor
public class ActaController {

    private final AdesUserService  userService;
    private final ActaQueryService query;

    /**
     * Verifica que el grupo consultado pertenezca al plantel del usuario cuando este
     * no tiene acceso global (nivelAcceso == 0, sin plantelId asignado). Antes de este
     * fix, materias()/periodos()/acta() no validaban el plantel del grupo pasado por
     * path/query param: un DIRECTOR o COORDINADOR_ACADEMICO (nivelAcceso 2-3) de un
     * plantel podía consultar el acta de calificaciones (nombres, CURP, promedios) de
     * un grupo de OTRO plantel simplemente adivinando/conociendo su UUID (BOLA, OWASP API1).
     */
    private void verificarPlantelDelGrupo(AdesUser user, UUID grupoId) {
        userService.verificarPlantel(user, query.plantelDeGrupo(grupoId), "No puede consultar un grupo de otro plantel");
    }

    /** Grupos UAEMEX vigentes para el selector. */
    @GetMapping("/grupos")
    public ResponseEntity<List<Map<String, Object>>> grupos(
            @RequestParam(value = "plantel_id", required = false) UUID plantelId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 3) {
            return ResponseEntity.status(403).build();
        }
        // No-admins (plantelId propio asignado) quedan acotados a su plantel — el
        // parámetro de query ya NO puede sobreescribirlo (antes sí, permitiendo a un
        // director/coordinador consultar grupos de otro plantel a voluntad).
        UUID effectivePlantel = user.getPlantelId() != null ? user.getPlantelId() : plantelId;
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
        verificarPlantelDelGrupo(user, grupoId);
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
        verificarPlantelDelGrupo(user, grupoId);
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
        verificarPlantelDelGrupo(user, grupoId);
        Map<String, Object> result = query.acta(grupoId, materiaId);
        if (result.containsKey("error")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
