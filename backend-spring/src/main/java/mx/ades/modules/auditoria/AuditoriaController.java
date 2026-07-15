package mx.ades.modules.auditoria;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.auditoria.query.AuditoriaQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

/**
 * Adaptador REST para la consulta del registro de auditoría del sistema.
 * Expone un único endpoint GET bajo /api/v1/auditoria que devuelve el log
 * de cambios con hash MD5 encadenado almacenado en {@code auditoria.log_auditoria}.
 * Filtrable por entidad, acción, usuario y límite de registros.
 * Acceso exclusivo para ADMIN_GLOBAL (nivelAcceso = 0 en esta implementación).
 * Los datos de auditoría contienen trazabilidad completa de INSERT/UPDATE/DELETE
 * con identificación del usuario que realizó cada operación.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AdesUserService userService;
    private final AuditoriaQueryService queryService;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${authentik.api-token:}")
    private String authentikApiToken;

    private static final String AUTHENTIK_URL = "http://authentik-server:9000";

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listarAuditLog(
            @RequestParam(value = "entidad", required = false) String entidad,
            @RequestParam(value = "accion", required = false) String accion,
            @RequestParam(value = "usuario_id", required = false) UUID usuarioId,
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "50") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL tiene acceso al registro de auditoría");
        }
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);
        return ResponseEntity.ok(queryService.listar(entidad, accion, usuarioId, pagina, porPagina));
    }

    /**
     * AD-007: consulta la API de Eventos de Authentik para auditar intentos de
     * login fallidos — Authentik es dueño de la autenticación (OIDC), ADES no
     * intercepta credenciales; solo consulta su propio log de eventos con un
     * token de solo lectura (intent=api, ver .env AUTHENTIK_API_TOKEN).
     */
    @GetMapping("/intentos-fallidos")
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<Map<String, Object>>> intentosFallidos(
            @RequestParam(value = "pagina", defaultValue = "1") int pagina,
            @RequestParam(value = "por_pagina", defaultValue = "50") int porPagina,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL tiene acceso al registro de auditoría");
        }
        if (authentikApiToken == null || authentikApiToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTHENTIK_API_TOKEN no configurado — ver .env");
        }
        pagina = Math.max(pagina, 1);
        porPagina = Math.min(Math.max(porPagina, 1), 200);

        try {
            Map<String, Object> resp = restClient.get()
                    .uri(AUTHENTIK_URL + "/api/v3/events/events/?action=login_failed&ordering=-created&page_size="
                            + porPagina + "&page=" + pagina)
                    .header("Authorization", "Bearer " + authentikApiToken)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> results = resp != null
                    ? (List<Map<String, Object>>) resp.getOrDefault("results", List.of())
                    : List.of();

            List<Map<String, Object>> simplificado = new ArrayList<>();
            for (Map<String, Object> e : results) {
                Map<String, Object> context = (Map<String, Object>) e.getOrDefault("context", Map.of());
                simplificado.add(Map.of(
                        "fecha", e.getOrDefault("created", ""),
                        "usuario_intentado", context.getOrDefault("username", "desconocido"),
                        "ip_origen", e.getOrDefault("client_ip", ""),
                        "app", e.getOrDefault("app", "")
                ));
            }
            return ResponseEntity.ok(simplificado);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al consultar la API de Eventos de Authentik: " + e.getMessage());
        }
    }
}
