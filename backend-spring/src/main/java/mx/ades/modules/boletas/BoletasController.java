package mx.ades.modules.boletas;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/boletas")
@RequiredArgsConstructor
public class BoletasController {

    private final AdesUserService userService;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/boletas";

    @GetMapping("/{estudiante_id}")
    public ResponseEntity<byte[]> generarBoleta(
            @PathVariable("estudiante_id") UUID estudianteId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            String url = API_BASE_URL + "/" + estudianteId;
            if (cicloId != null) {
                url += "?ciclo_id=" + cicloId;
            }

            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(url);
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            if (response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION) != null) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            }

            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar boleta en microservicio FastAPI: " + e.getMessage());
        }
    }

    @PostMapping("/grupo/{grupo_id}/batch")
    public ResponseEntity<Map<String, Object>> encolarBoletasGrupo(
            @PathVariable("grupo_id") UUID grupoId,
            @RequestParam(value = "ciclo_id", required = false) UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            String url = API_BASE_URL + "/grupo/" + grupoId + "/batch";
            if (cicloId != null) {
                url += "?ciclo_id=" + cicloId;
            }

            RestClient.RequestBodySpec request = restClient.post()
                    .uri(url);
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al encolar boletas en microservicio FastAPI: " + e.getMessage());
        }
    }

    @GetMapping("/tarea/{task_id}")
    public ResponseEntity<Map<String, Object>> estadoTarea(
            @PathVariable("task_id") String taskId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(API_BASE_URL + "/tarea/" + taskId);
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al consultar estado de tarea en microservicio: " + e.getMessage());
        }
    }
}
