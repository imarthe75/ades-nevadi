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

/**
 * Adaptador REST como proxy de autenticación para Apache Superset BI.
 * Expone endpoints bajo /api/v1/superset para obtener un guest token de Superset
 * con Row-Level Security (RLS) dinámico según el nivelAcceso del usuario:
 * AdminGlobal sin restricciones, AdminPlantel/Director/Coordinador filtrado por plantel_id,
 * Docente filtrado por profesor_id, Alumno filtrado por estudiante_id.
 * El endpoint /dashboard/{key} selecciona automáticamente el dashboard apropiado
 * según el rol del usuario, y /dashboards lista los dashboards disponibles y su configuración.
 * Requiere JWT válido en todos los endpoints; las credenciales de Superset se leen de propiedades.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/superset")
@RequiredArgsConstructor
@Slf4j
public class SupersetController {

    private final AdesUserService userService;
    private final RestClient restClient = RestClient.create();

    @Value("${superset.url}")
    private String supersetUrl;

    // URL pública (bi.ades.setag.mx) — solo para el embedUrl devuelto al navegador;
    // las llamadas servidor-a-servidor de este controller siguen usando supersetUrl
    // (hostname interno de Docker).
    @Value("${superset.public-url}")
    private String supersetPublicUrl;

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

    /**
     * Superset exige X-CSRFToken en POST/PUT (incluido /security/guest_token/)
     * aunque la request ya vaya autenticada con Bearer JWT — sin esto, todo
     * guest token fallaba con 400 "The CSRF token is missing" (bug real
     * encontrado esta sesión, nunca había funcionado el embed).
     */
    private String obtenerCsrfToken(String accessToken) {
        try {
            Map<?, ?> resp = restClient.get()
                    .uri(supersetUrl + "/api/v1/security/csrf_token/")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.get("result") != null) {
                return (String) resp.get("result");
            }
        } catch (Exception e) {
            log.error("Superset csrf_token error", e);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo obtener CSRF token de Superset");
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

    /**
     * BOLA fix (auditoría 2026-07-15): antes solo se forzaba el {@code key} propio del
     * usuario cuando el path param pedido era literalmente "instituto"; si un usuario
     * de nivel &gt;0 pedía directamente {@code /dashboard/docente} o {@code /dashboard/plantel}
     * el valor del cliente se usaba tal cual para resolver el dashboardId (el RLS seguía
     * calculándose sobre el usuario real, pero exponía la superficie a un dashboard cuyo
     * dataset podría no tener la columna filtrada por ese nivel, rompiendo el aislamiento
     * esperado). Ahora se ignora el {@code key} del cliente para cualquier usuario que no
     * sea ADMIN_GLOBAL (nivelAcceso 0): siempre se sirve el dashboard correspondiente a su
     * propio rol, sin excepción.
     */
    private String resolverDashboardKey(AdesUser user, String requestedKey) {
        String rolKey = NIVEL_A_KEY.getOrDefault(user.getNivelAcceso(), "alumno");
        if (user.getNivelAcceso() != null && user.getNivelAcceso() == 0) {
            return requestedKey; // ADMIN_GLOBAL puede previsualizar cualquier dashboard
        }
        return rolKey;
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
        String effectiveKey = resolverDashboardKey(user, key);

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
            String csrfToken = obtenerCsrfToken(accessToken);
            Map<?, ?> resp = restClient.post()
                    .uri(supersetUrl + "/api/v1/security/guest_token/")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-CSRFToken", csrfToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(guestPayload)
                    .retrieve()
                    .body(Map.class);

            if (resp != null && resp.containsKey("token")) {
                GuestTokenResponse res = new GuestTokenResponse();
                res.setToken((String) resp.get("token"));
                res.setDashboardId(dashboardId);
                res.setEmbedUrl(supersetPublicUrl + "/superset/embedded/" + dashboardId);
                return ResponseEntity.ok(res);
            }
        } catch (Exception e) {
            log.error("Superset guest token error", e);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Error al obtener token del dashboard");
    }

    /**
     * IA-020: exporta a CSV los datos de todos los charts de un dashboard, empacados
     * en un ZIP (uno por chart). Reusa el mismo login admin que el guest token;
     * para cada chart obtiene su query_context ya guardado (vía GET /chart/{id})
     * y lo reenvía a POST /chart/data con result_format=csv — el mismo mecanismo
     * que usa la propia UI de Superset para "Download as CSV" por chart.
     */
    @GetMapping("/dashboard/{key}/export-csv")
    public ResponseEntity<byte[]> exportarCsv(
            @PathVariable("key") String key,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        String effectiveKey = resolverDashboardKey(user, key);
        String dashboardId = getDashboardId(effectiveKey);
        if (dashboardId == null || dashboardId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard '" + effectiveKey + "' no configurado.");
        }

        String accessToken = supersetLogin();
        String csrfToken = obtenerCsrfToken(accessToken);

        List<Map<String, Object>> charts;
        try {
            Map<?, ?> resp = restClient.get()
                    .uri(supersetUrl + "/api/v1/dashboard/" + dashboardId + "/charts")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            charts = resp != null ? (List<Map<String, Object>>) resp.get("result") : List.of();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al listar charts del dashboard: " + e.getMessage());
        }

        java.io.ByteArrayOutputStream zipBytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(zipBytes)) {
            for (Map<String, Object> chart : charts) {
                Object chartIdObj = chart.get("id");
                String nombreChart = String.valueOf(chart.getOrDefault("slice_name", "chart_" + chartIdObj));
                try {
                    Map<?, ?> chartDetail = restClient.get()
                            .uri(supersetUrl + "/api/v1/chart/" + chartIdObj)
                            .header("Authorization", "Bearer " + accessToken)
                            .retrieve()
                            .body(Map.class);
                    Object result = chartDetail != null ? chartDetail.get("result") : null;
                    if (!(result instanceof Map)) continue;
                    Object queryContextRaw = ((Map<?, ?>) result).get("query_context");
                    if (queryContextRaw == null) continue;

                    Map<String, Object> queryContext = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue((String) queryContextRaw, Map.class);
                    queryContext.put("result_format", "csv");
                    queryContext.put("result_type", "full");

                    byte[] csv = restClient.post()
                            .uri(supersetUrl + "/api/v1/chart/data")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("X-CSRFToken", csrfToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(queryContext)
                            .retrieve()
                            .body(byte[].class);
                    if (csv == null) continue;

                    String entryName = (nombreChart.replaceAll("[^a-zA-Z0-9 _-]", "") + ".csv").trim();
                    zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                    zos.write(csv);
                    zos.closeEntry();
                } catch (Exception chartEx) {
                    log.warn("No se pudo exportar chart {} ({}): {}", chartIdObj, nombreChart, chartEx.getMessage());
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar ZIP de exportación: " + e.getMessage());
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header("Content-Disposition", "attachment; filename=reporte_bi_" + effectiveKey + ".zip")
                .body(zipBytes.toByteArray());
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
            // Map.of() no acepta valores null (dbId es null cuando el dashboard no está
            // configurado aún) — usar HashMap mutable en su lugar para no lanzar NPE.
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", configured ? dbId : null);
            entry.put("configured", configured);
            dashboards.put(k, entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rol_key", rolKey);
        result.put("dashboards", dashboards);
        return ResponseEntity.ok(result);
    }
}
