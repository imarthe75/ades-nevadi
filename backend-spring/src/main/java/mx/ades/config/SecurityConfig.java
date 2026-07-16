package mx.ades.config;

import mx.ades.security.AdesJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestOperations;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authorize -> authorize
                // ADES internos y documentación
                .requestMatchers("/api/v1/health", "/api/v1/auth/callback", "/api/v1/auth/refresh",
                        "/api/v1/certificados/verificar/**", "/api/v1/config/public",
                        "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                // Portal público — sin autenticación (JWT validado manualmente en controllers)
                .requestMatchers("/api/portal/**").permitAll()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .jwtAuthenticationConverter(adesJwtConverter())
            ));
        return http.build();
    }

    @Bean
    public AdesJwtConverter adesJwtConverter() {
        return new AdesJwtConverter();
    }

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${ades.oidc.client-id}")
    private String oidcClientId;

    /**
     * Validador de audiencia (2026-07-16, docs/hallazgos/
     * 2026-07-16_auditoria_gaps_no_revisados.md #3 — hueco confirmado por auditoría):
     * {@code NimbusJwtDecoder.withJwkSetUri(...).build()} sin {@code .jwtValidator()}
     * adicional solo valida firma + {@code exp}/{@code nbf} — nunca {@code aud}. Riesgo
     * de "confused deputy": un JWT válido firmado por el mismo Authentik pero emitido
     * para OTRA aplicación OIDC de la misma instancia (ej. Superset, que comparte el
     * mismo servidor Authentik) podría ser aceptado por este BFF si comparte clave de
     * firma. FastAPI (core/security.py) ya validaba {@code audience=OIDC_CLIENT_ID}
     * correctamente — este fix alinea el lado Spring con el mismo criterio.
     */
    private OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(oidcClientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "El token no fue emitido para esta aplicación (aud)", null));
        };
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));

        RestTemplate restTemplate = new RestTemplate(factory);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).restOperations(restTemplate).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), audienceValidator()));
        return decoder;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://ades.setag.mx",
                "https://portalnvd.setag.mx",
                "http://localhost:4200",
                "http://localhost:4201"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
