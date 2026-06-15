package mx.ades.controller;

import lombok.RequiredArgsConstructor;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final AdesUserService userService;

    @Value("${ades.oidc.client-secret:}")
    private String clientSecret;

    @Value("${oidc.token-url:http://authentik-server:9000/application/o/token/}")
    private String tokenUrl;

    @Value("${oidc.client-id:ades-frontend}")
    private String clientId;

    @Value("${oidc.redirect-uri:https://ades.setag.mx/callback}")
    private String redirectUri;

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> callback(
            @RequestParam("code") String code,
            @RequestParam("code_verifier") String codeVerifier) {
        try {
            RestClient restClient = RestClient.builder()
                    .requestFactory(new SimpleClientHttpRequestFactory())
                    .build();

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("code", code);
            body.add("redirect_uri", redirectUri);
            body.add("client_id", clientId);
            body.add("code_verifier", codeVerifier);
            if (clientSecret != null && !clientSecret.isEmpty()) {
                body.add("client_secret", clientSecret);
            }

            ResponseEntity<String> response = restClient.post()
                    .uri(tokenUrl)
                    .header("Host", "auth.ades.setag.mx")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(response.getHeaders().getContentType())
                    .body(response.getBody());
        } catch (HttpClientErrorException e) {
            MediaType contentType = (e.getResponseHeaders() != null) ? e.getResponseHeaders().getContentType() : MediaType.APPLICATION_JSON;
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(contentType)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "token_exchange_failed");
            err.put("message", e.getMessage());
            return ResponseEntity.status(502).body(err);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        Map<String, Object> out = new HashMap<>();
        out.put("sub", jwt.getSubject());
        out.put("email", user.getEmail());
        out.put("nombre_usuario", user.getUsername());
        out.put("roles", user.getRoles());
        out.put("plantel_id", user.getPlantelId());
        out.put("nivel_educativo_id", null);
        out.put("nivel_acceso", user.getNivelAcceso());
        return ResponseEntity.ok(out);
    }
}
