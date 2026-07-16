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
        // (Corregido 2026-07-16: el chequeo original solo forzaba el plantel propio si
        // el request NO traía plantel_id — un Coordinador podía pasar explícitamente el
        // plantel_id de otro plantel y consultarlo. Ahora usa getEffectivePlantelId, que
        // siempre fuerza el plantel propio para no-admins sin importar el valor pedido.)
        UUID scope = userService.getEffectivePlantelId(user, plantelId);
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
        // BOLA fix: grupos() ya scopea por plantel, pero este endpoint no verificaba que el
        // grupo_id perteneciera al plantel del usuario — un Coordinador podía ver el roster
        // de un grupo de OTRO plantel con solo el UUID.
        verificarPlantelDeGrupo(user, grupoId);
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
        // BOLA fix: mismo criterio que alumnosGrupo — sin esto, un Coordinador podía consultar
        // el kardex (historial académico completo) de un alumno de OTRO plantel por UUID.
        verificarPlantelDeEstudiante(user, estudianteId);
        Map<String, Object> kardex = query.kardex(estudianteId, cicloId);
        if (kardex.get("alumno") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sin kardex UAEMEX para el estudiante/ciclo indicado");
        }
        return ResponseEntity.ok(kardex);
    }

    private void verificarPlantelDeGrupo(AdesUser user, UUID grupoId) {
        UUID plantelGrupo = query.plantelIdDeGrupo(grupoId);
        if (plantelGrupo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo no encontrado");
        userService.verificarPlantel(user, plantelGrupo, "El grupo no pertenece a su plantel");
    }

    private void verificarPlantelDeEstudiante(AdesUser user, UUID estudianteId) {
        UUID plantelEstudiante = query.plantelIdDeEstudiante(estudianteId);
        if (plantelEstudiante == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado");
        userService.verificarPlantel(user, plantelEstudiante, "El alumno no pertenece a su plantel");
    }
}
