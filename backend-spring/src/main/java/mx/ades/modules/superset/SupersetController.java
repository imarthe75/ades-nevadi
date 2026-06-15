package mx.ades.modules.superset;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/superset")
@RequiredArgsConstructor
@Slf4j
public class SupersetController {

    private final AdesUserService userService;
    private final RestClient restClient = RestClient.create();

    @Value("${superset.url}")
    private String supersetUrl;

    @Value("${superset.admin-user}")
    private String supersetUser;

    @Value("${superset.admin-password}")
    private String supersetPassword;

    @Value("${superset.dashboards.instituto:}")
    private String dashboardInstituto;

    @Value("${superset.dashboards.plantel:}")
    private String dashboardPlantel;

    @Value("${superset.dashboards.docente:}")
    private String dashboardDocente;

    @Value("${superset.dashboards.alumno:}")
    private String dashboardAlumno;

    private static final Map<Integer, String> NIVEL_A_KEY = Map.of(
            0, "instituto",
            1, "plantel",
            2, "plantel",
            3, "plantel",
            4, "docente",
            5, "alumno"
    );

    @Data
    public static class GuestTokenResponse {
        private String token;
        private String dashboardId;
        private String embedUrl;
    }

    private String supersetLogin() {
        try {
            Map<String, Object> body = Map.of(
                    "username", supersetUser,
                    "password", supersetPassword,
                    "provider", "db",
                    "refresh", false
            );

            Map<?, ?> resp = restClient.post()
                    .uri(supersetUrl + "/api/v1/security/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp != null && resp.containsKey("access_token")) {
                return (String) resp.get("access_token");
            }
        } catch (Exception e) {
            log.error("Superset login failed", e);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo conectar con el servicio de dashboards");
    }

    private List<Map<String, Object>> buildRls(AdesUser user) {
        int nivel = user.getNivelAcceso() != null ? user.getNivelAcceso() : 5;
        List<Map<String, Object>> rls = new ArrayList<>();

        if (nivel == 0) {
            return rls; // ADMIN_GLOBAL
        }

        if (nivel <= 2 && user.getPlantelId() != null) {
            Map<String, Object> clause = new HashMap<>();
            clause.put("clause", "plantel_id = '" + user.getPlantelId() + "'");
            clause.put("dataset", null);
            rls.add(clause);
        } else if (nivel == 4 && user.getPersonaId() != null) {
            Map<String, Object> clause = new HashMap<>();
            clause.put("clause", "profesor_id = '" + user.getPersonaId() + "'");
            clause.put("dataset", null);
            rls.add(clause);
        } else if (nivel == 5 && user.getPersonaId() != null) {
            Map<String, Object> clause = new HashMap<>();
            clause.put("clause", "estudiante_id = '" + user.getPersonaId() + "'");
            clause.put("dataset", null);
            rls.add(clause);
        }
        return rls;
    }

    private String getDashboardId(String key) {
        return switch (key) {
            case "instituto" -> dashboardInstituto;
            case "plantel" -> dashboardPlantel;
            case "docente" -> dashboardDocente;
            case "alumno" -> dashboardAlumno;
            default -> "";
        };
    }

    @GetMapping("/dashboard/{key}")
    public ResponseEntity<GuestTokenResponse> getGuestToken(
            @PathVariable("key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);

        String effectiveKey = key;
        String rolKey = NIVEL_A_KEY.getOrDefault(user.getNivelAcceso(), "alumno");
        if ("instituto".equals(key) && (user.getNivelAcceso() == null || user.getNivelAcceso() > 0)) {
            effectiveKey = rolKey;
        }

        String dashboardId = getDashboardId(effectiveKey);
        if (dashboardId == null || dashboardId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Dashboard '" + effectiveKey + "' no configurado.");
        }

        String accessToken = supersetLogin();
        List<Map<String, Object>> rls = buildRls(user);

        Map<String, Object> guestPayload = Map.of(
                "resources", List.of(Map.of("type", "dashboard", "id", dashboardId)),
                "rls", rls,
                "user", Map.of(
                        "username", user.getUsername(),
                        "first_name", user.getUsername(),
                        "last_name", ""
                )
        );

        try {
            Map<?, ?> resp = restClient.post()
                    .uri(supersetUrl + "/api/v1/security/guest_token/")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(guestPayload)
                    .retrieve()
                    .body(Map.class);

            if (resp != null && resp.containsKey("token")) {
                GuestTokenResponse res = new GuestTokenResponse();
                res.setToken((String) resp.get("token"));
                res.setDashboardId(dashboardId);
                res.setEmbedUrl(supersetUrl + "/superset/embedded/" + dashboardId);
                return ResponseEntity.ok(res);
            }
        } catch (Exception e) {
            log.error("Superset guest token error", e);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Error al obtener token del dashboard");
    }

    @GetMapping("/dashboards")
    public ResponseEntity<Map<String, Object>> listAvailableDashboards(
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String rolKey = NIVEL_A_KEY.getOrDefault(user.getNivelAcceso(), "alumno");

        Map<String, Object> dashboards = new LinkedHashMap<>();
        for (String k : List.of("instituto", "plantel", "docente", "alumno")) {
            String dbId = getDashboardId(k);
            boolean configured = dbId != null && !dbId.isBlank();
            dashboards.put(k, Map.of(
                    "id", configured ? dbId : null,
                    "configured", configured
            ));
        }

        Map<String, Object> result = Map.of(
                "rol_key", rolKey,
                "dashboards", dashboards
        );
        return ResponseEntity.ok(result);
    }
}
