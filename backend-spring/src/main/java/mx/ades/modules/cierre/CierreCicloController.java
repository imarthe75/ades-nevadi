package mx.ades.modules.cierre;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cierre-ciclo")
@RequiredArgsConstructor
public class CierreCicloController {

    private final CierreCicloService service;
    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/pdf";

    @Data
    public static class CierreCicloRequest {
        private UUID cicloOrigenId; // Keep old field for compatibility if needed
        private UUID cicloDestinoId;
    }

    @GetMapping("/{ciclo_id}/indicadores")
    public ResponseEntity<Map<String, Object>> obtenerIndicadores(
            @PathVariable("ciclo_id") UUID cicloId,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado.");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM v_indicadores_cierre_ciclo WHERE ciclo_escolar_id = ?", cicloId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontraron datos para el ciclo escolar especificado.");
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @PostMapping("/{ciclo_id}/acta-inicio")
    public ResponseEntity<byte[]> actaInicio(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado.");
        }

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.post()
                    .uri(API_BASE_URL + "/" + cicloId + "/acta-inicio");
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar acta en microservicio: " + e.getMessage());
        }
    }

    @PostMapping("/{ciclo_id}/acta-cierre")
    public ResponseEntity<byte[]> actaCierre(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado.");
        }

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.post()
                    .uri(API_BASE_URL + "/" + cicloId + "/acta-cierre");
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar acta en microservicio: " + e.getMessage());
        }
    }

    @PostMapping("/{ciclo_id}/ejecutar")
    public ResponseEntity<Map<String, Object>> ejecutarCierreCiclo(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestBody CierreCicloRequest payload,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administradores o directores pueden realizar el cierre de ciclo.");
        }

        // Verify cycle exists and is not CLOSED
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, estado FROM ades_ciclos_escolares WHERE id = ? AND is_active = TRUE", cicloId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciclo escolar no encontrado.");
        }
        String estado = (String) rows.get(0).get("estado");
        if ("CERRADO".equals(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ciclo escolar ya se encuentra cerrado.");
        }

        // Close cycle
        jdbc.update(
                "UPDATE ades_ciclos_escolares SET estado = 'CERRADO', es_vigente = FALSE, fecha_modificacion = now() WHERE id = ?",
                cicloId);

        String resultadoPromo = null;
        if (payload.getCicloDestinoId() != null) {
            resultadoPromo = service.cerrarCiclo(cicloId, payload.getCicloDestinoId(), user.getUsername());
        }

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Ciclo escolar cerrado exitosamente.",
                "resultado_promocion", resultadoPromo != null ? resultadoPromo : ""
        ));
    }

    // Keep old API compatibility
    @PostMapping
    public ResponseEntity<String> cerrar(@RequestBody CierreCicloRequest request, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String result = service.cerrarCiclo(request.getCicloOrigenId(), request.getCicloDestinoId(), user.getUsername());
        return ResponseEntity.ok(result);
    }
}
