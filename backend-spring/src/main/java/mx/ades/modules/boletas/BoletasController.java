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

/**
 * Adaptador REST para la generación de boletas de calificaciones en PDF.
 * Expone endpoints bajo /api/v1/boletas que actúan como proxy hacia el
 * microservicio FastAPI (Python/WeasyPrint). No genera documentos directamente:
 * delega la renderización al use case {@code GenerarBoletaUseCase}.
 * Soporta boleta NEM (primaria/secundaria SEP, escala 6-10 o cualitativa A/B/C/D),
 * boleta UAEMEX (preparatoria CBU, escala 0-10 con ordinario/extra/definitiva)
 * y generación batch por grupo con seguimiento de tarea asíncrona (taskId).
 * Toda solicitud requiere JWT válido via {@code resolveUser}.
 *
 * @author ADES
 * @since 2026
 */
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

    @GetMapping("/uaemex/{estudiante_id}")
    public ResponseEntity<byte[]> generarConstanciaUaemex(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return generarUseCase.generarUaemex(estudianteId, cicloId, authHeader);
    }
}
