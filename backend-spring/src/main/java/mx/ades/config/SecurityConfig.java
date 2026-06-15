package mx.ades.config;

import mx.ades.security.AdesJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/health", "/api/v1/auth/callback", "/api/v1/certificados/verificar/**", "/api/v1/config/public").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
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
}
