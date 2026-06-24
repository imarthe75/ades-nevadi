package mx.ades.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversor de JWT de Authentik al token de autenticación interno de Spring Security.
 * <p>
 * Actúa como adaptador de entrada de seguridad en la capa hexagonal: transforma
 * el JWT emitido por Authentik (OIDC) en un {@link JwtAuthenticationToken} con
 * las {@link GrantedAuthority} derivadas de los claims {@code groups} o {@code roles}.
 * El {@code principalName} se establece a {@code preferred_username} cuando está
 * presente, cayendo a {@code sub} como respaldo.
 * </p>
 * <p>
 * Este convertidor debe registrarse en la configuración de seguridad HTTP via
 * {@code jwtAuthenticationConverter(new AdesJwtConverter())} para que todos los
 * endpoints protejan correctamente los recursos multi-plantel de ADES.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
public class AdesJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String principalClaimName = jwt.getClaimAsString("preferred_username") != null 
                ? "preferred_username" 
                : "sub";
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString(principalClaimName));
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("groups");
        if (groups == null || groups.isEmpty()) {
            groups = jwt.getClaimAsStringList("roles");
        }
        if (groups == null) {
            return Collections.emptyList();
        }
        return groups.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
