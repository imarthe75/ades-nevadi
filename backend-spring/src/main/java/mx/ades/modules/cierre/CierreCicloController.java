package mx.ades.modules.cierre;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import mx.ades.modules.cierre.domain.port.in.CerrarCicloUseCase;
import mx.ades.modules.cierre.query.CierreQueryService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cierre-ciclo")
@RequiredArgsConstructor
public class CierreCicloController {

    private final CierreCicloService service;
    private final AdesUserService userService;
    private final CerrarCicloUseCase cerrarCicloUseCase;
    private final CierreQueryService queryService;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/pdf";

    @Data
    public static class CierreCicloRequest {
        private UUID cicloOrigenId;
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
        return ResponseEntity.ok(queryService.indicadores(cicloId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No se encontraron datos para el ciclo escolar especificado.")));
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
        return proxyPost(API_BASE_URL + "/" + cicloId + "/acta-inicio", authHeader);
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
        return proxyPost(API_BASE_URL + "/" + cicloId + "/acta-cierre", authHeader);
    }

    @PostMapping("/{ciclo_id}/ejecutar")
    public ResponseEntity<Map<String, Object>> ejecutarCierreCiclo(
            @PathVariable("ciclo_id") UUID cicloId,
            @RequestBody CierreCicloRequest payload,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        try {
            String resultadoPromo = cerrarCicloUseCase.cerrar(
                new CerrarCicloUseCase.Command(cicloId, payload.getCicloDestinoId(), user.getNivelAcceso(), user.getUsername()));
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Ciclo escolar cerrado exitosamente.",
                "resultado_promocion", resultadoPromo != null ? resultadoPromo : ""));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("no encontrado")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
    }

    @PostMapping
    public ResponseEntity<String> cerrar(@RequestBody CierreCicloRequest request, @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String result = service.cerrarCiclo(request.getCicloOrigenId(), request.getCicloDestinoId(), user.getUsername());
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<byte[]> proxyPost(String url, String authHeader) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.post().uri(url);
            if (authHeader != null) request.header(HttpHeaders.AUTHORIZATION, authHeader);
            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (cd != null) headers.set(HttpHeaders.CONTENT_DISPOSITION, cd);
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar acta en microservicio: " + e.getMessage());
        }
    }
}
