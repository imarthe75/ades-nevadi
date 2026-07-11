package mx.ades.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Equivalente Spring del AuditMiddleware de FastAPI.
 * Registra toda mutación HTTP (POST/PUT/PATCH/DELETE) en ades_audit_log.
 * CLAUDE.md: "Endpoints mutantes (POST/PUT/PATCH/DELETE): siempre pasan por AuditMiddleware"
 */
@Component
@Order(200)
@RequiredArgsConstructor
@Slf4j
public class AuditHttpFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditLogWriter auditLogWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();
        if (!MUTATING_METHODS.contains(method) || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            // Auditar tanto éxitos como errores de cliente; omitir errores del servidor (5xx transitorios)
            if (status < 500) {
                String sub = extractSub();
                String uri = request.getRequestURI();
                // Derivar "entidad" del segundo segmento de la ruta (/api/v1/{entidad}/...)
                String entidad = deriveEntidad(uri);
                try {
                    auditLogWriter.registrar(sub, method, entidad, uri, (short) status,
                        (int) (System.currentTimeMillis() - start));
                } catch (Exception ex) {
                    // No interrumpir el flujo principal por fallos de auditoría
                    log.warn("AuditHttpFilter: no se pudo registrar en ades_audit_log — {}", ex.getMessage());
                }
            }
        }
    }

    private String extractSub() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                return jwt.getSubject();
            }
        } catch (Exception ignored) { /* silent */ }
        return "anonymous";
    }

    private static String deriveEntidad(String uri) {
        // /api/v1/{entidad}/... → entidad
        String[] parts = uri.split("/");
        return parts.length >= 4 ? parts[3] : uri;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.startsWith("/api/v1/health");
    }
}
