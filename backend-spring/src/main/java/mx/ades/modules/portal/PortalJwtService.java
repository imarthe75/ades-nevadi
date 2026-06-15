package mx.ades.modules.portal;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT propio para usuarios del portal externo (portalnvd.setag.mx).
 * Usa HMAC-SHA256 con PORTAL_JWT_SECRET — independiente de Authentik.
 * Secret mínimo: 32 caracteres.
 */
@Service
@Slf4j
public class PortalJwtService {

    @Value("${portal.jwt.secret}")
    private String secret;

    @Value("${portal.jwt.expiry-hours:24}")
    private long expiryHours;

    private static final String ISSUER = "ades-portal";

    public String generarToken(UUID usuarioId, String email, String nombreCompleto) {
        try {
            JWSSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(usuarioId.toString())
                    .claim("email", email)
                    .claim("nombre", nombreCompleto)
                    .issuer(ISSUER)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(expiryHours * 3600)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            log.error("Error generando JWT portal: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error generando token");
        }
    }

    public JWTClaimsSet validarToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret.getBytes(StandardCharsets.UTF_8));
            if (!jwt.verify(verifier)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expirado");
            }
            if (!ISSUER.equals(claims.getIssuer())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token no válido para este servicio");
            }
            return claims;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("JWT portal inválido: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido o expirado");
        }
    }

    /** Extrae claims del header Authorization: Bearer <token>. Lanza 401 si no hay token válido. */
    public JWTClaimsSet resolverDesdeRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación de portal");
        }
        return validarToken(header.substring(7));
    }

    public UUID resolverUsuarioId(HttpServletRequest request) {
        return UUID.fromString(resolverDesdeRequest(request).getSubject());
    }
}
