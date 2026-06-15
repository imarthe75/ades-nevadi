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
