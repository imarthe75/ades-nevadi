package mx.ades.modules.boletas;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.boletas.domain.port.in.GenerarBoletaUseCase;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/boletas")
@RequiredArgsConstructor
public class BoletasController {

    private final AdesUserService userService;
    private final GenerarBoletaUseCase generarUseCase;

    @GetMapping("/{estudiante_id}")
    public ResponseEntity<byte[]> generarBoleta(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return generarUseCase.generar(estudianteId, cicloId, authHeader);
    }

    @PostMapping("/grupo/{grupo_id}/batch")
    public ResponseEntity<Map<String, Object>> encolarBoletasGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return generarUseCase.encolarGrupo(grupoId, cicloId, authHeader);
    }

    @GetMapping("/tarea/{task_id}")
    public ResponseEntity<Map<String, Object>> estadoTarea(
            @PathVariable("task_id") String taskId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return generarUseCase.estadoTarea(taskId, authHeader);
    }
}
