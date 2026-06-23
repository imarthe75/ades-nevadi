package mx.ades.modules.grupos;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.grupos.query.GrupoQueryService;
import mx.ades.security.AdesUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final AdesUserService userService;
    private final GrupoQueryService queryService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "plantel_id", required = false) UUID plantelId,
            @RequestParam(name = "ciclo_id", required = false) UUID cicloId,
            @RequestParam(name = "ciclo_escolar_id", required = false) UUID cicloEscolarId,
            @RequestParam(name = "grado_id", required = false) UUID gradoId,
            @RequestParam(name = "nivel_id", required = false) UUID nivelId,
            @RequestParam(name = "solo_activos", defaultValue = "true") boolean soloActivos,
            @RequestParam(name = "ciclo_vigente", defaultValue = "false") boolean cicloVigente) {
        userService.resolveUser(jwt);
        UUID effectiveCicloId = cicloId != null ? cicloId : cicloEscolarId;
        return ResponseEntity.ok(queryService.listar(plantelId, effectiveCicloId, gradoId, nivelId, soloActivos, cicloVigente));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return ResponseEntity.ok(queryService.obtener(id));
    }
}
