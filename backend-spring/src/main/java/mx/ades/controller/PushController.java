package mx.ades.controller;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final AdesUserService userService;

    @Value("${ntfy.url:http://ades-ntfy:80}")
    private String ntfyUrl;

    @Value("${ntfy.public-url:${NTFY_PUBLIC_URL:https://ades.setag.mx/ntfy}}")
    private String ntfyPublicUrl;

    @Value("${ntfy.admin-token:}")
    private String adminToken;

    /**
     * Devuelve el topic ntfy personal del usuario autenticado y la URL SSE
     * para que el frontend abra la conexión EventSource.
     *
     * Respuesta:
     *   topic            — nombre del topic (ades_<userId>)
     *   url_sse          — URL completa del stream SSE en ntfy
     *   token_requerido  — si el servidor ntfy requiere token de autenticación
     */
    @GetMapping("/suscripcion")
    public ResponseEntity<Map<String, Object>> suscripcion(
            @AuthenticationPrincipal Jwt jwt) {

        AdesUser user = userService.resolveUser(jwt);

        String topic   = "ades_" + user.getId();
        String baseUrl = ntfyPublicUrl.endsWith("/")
                ? ntfyPublicUrl.stripTrailing()
                : ntfyPublicUrl;
        String urlSse  = baseUrl + "/" + topic + "/sse";

        return ResponseEntity.ok(Map.of(
                "topic",           topic,
                "url_sse",         urlSse,
                "token_requerido", adminToken != null && !adminToken.isBlank()
        ));
    }
}
