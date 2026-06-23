package mx.ades.modules.sepomex;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.admin.domain.model.PermisoAdmin;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proxy BFF → FastAPI para disparar y consultar la sincronización manual del
 * catálogo SEPOMEX. Solo ADMIN_GLOBAL (nivelAcceso 0).
 *
 * POST /api/v1/admin/sepomex/sync         → encola task Celery sync_sepomex_weekly
 * GET  /api/v1/admin/sepomex/sync/{id}    → estado del task
 */
@RestController
@RequestMapping("/api/v1/admin/sepomex")
@RequiredArgsConstructor
public class SepomexAdminController {

    private static final String FASTAPI_BASE = "http://ades-api:8000/api/v1/sepomex";

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final RestClient restClient = RestClient.create();

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(@AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("estados",          jdbc.queryForObject("SELECT COUNT(*) FROM ades_estados", Long.class));
        res.put("municipios",       jdbc.queryForObject("SELECT COUNT(*) FROM ades_municipios", Long.class));
        res.put("colonias",         jdbc.queryForObject("SELECT COUNT(*) FROM ades_codigos_postales", Long.class));
        res.put("cps_unicos",       jdbc.queryForObject("SELECT COUNT(DISTINCT codigo_postal) FROM ades_codigos_postales", Long.class));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> iniciarSync(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = new PermisoAdmin(user.getNivelAcceso() != null ? user.getNivelAcceso() : 99);
        if (!permiso.esAdminGlobal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL puede sincronizar SEPOMEX.");
        }

        try {
            RestClient.RequestBodySpec req = restClient.post().uri(FASTAPI_BASE + "/sync");
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = req.retrieve().body(Map.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al encolar sync SEPOMEX: " + e.getMessage());
        }
    }

    @GetMapping("/sync/{taskId}")
    public ResponseEntity<Map<String, Object>> estadoSync(
            @PathVariable String taskId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        AdesUser user = userService.resolveUser(jwt);
        PermisoAdmin permiso = new PermisoAdmin(user.getNivelAcceso() != null ? user.getNivelAcceso() : 99);
        if (!permiso.esAdminGlobal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo ADMIN_GLOBAL.");
        }

        try {
            RestClient.RequestHeadersSpec<?> req = restClient.get().uri(FASTAPI_BASE + "/sync/" + taskId);
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = req.retrieve().body(Map.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al consultar estado sync: " + e.getMessage());
        }
    }
}
