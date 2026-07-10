package mx.ades.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Filtro de rate limiting por IP
 * Se aplica a todas las requests entrantes
 *
 * Limites:
 * - /api/v1/auth/** → 5 req/min/IP
 * - /api/v1/** → 100 req/min/IP
 * - Excluye: /actuator, /swagger, /v3/api-docs
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingConfig.RateLimiter authRateLimiter;
    private final RateLimitingConfig.RateLimiter apiRateLimiter;

    private static final Pattern AUTH_PATTERN = Pattern.compile("^/api/v1/auth/.*");
    private static final Pattern API_PATTERN = Pattern.compile("^/api/v1/.*");

    public RateLimitingFilter(
            @Qualifier("authRateLimiter") RateLimitingConfig.RateLimiter authRateLimiter,
            @Qualifier("apiRateLimiter") RateLimitingConfig.RateLimiter apiRateLimiter) {
        this.authRateLimiter = authRateLimiter;
        this.apiRateLimiter = apiRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = getClientIP(request);

        try {
            // Determinar qué rate limiter usar
            RateLimitingConfig.RateLimiter limiter = null;
            String limitName = null;

            if (AUTH_PATTERN.matcher(path).matches()) {
                limiter = authRateLimiter;
                limitName = "auth";
            } else if (API_PATTERN.matcher(path).matches()) {
                limiter = apiRateLimiter;
                limitName = "api";
            }

            // Aplicar rate limiting si el patrón coincide
            if (limiter != null && !limiter.tryConsume(clientIp)) {
                log.warn("Rate limit exceeded for {} limiter, IP {} on path {}", limitName, clientIp, path);
                response.setStatus(429); // SC_TOO_MANY_REQUESTS — sin constante en jakarta.servlet.HttpServletResponse
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Too many requests\",\"status\":429,\"message\":\"Rate limit exceeded\"}");
                return;
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Error in rate limiting filter", e);
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Obtener IP del cliente considerando proxies (X-Forwarded-For, etc)
     */
    private String getClientIP(HttpServletRequest request) {
        String[] ipHeaders = {
            "X-Forwarded-For",
            "X-Real-IP",
            "CF-Connecting-IP",
            "True-Client-IP"
        };

        for (String header : ipHeaders) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !ip.equals("unknown")) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // Excluir health checks y swagger del rate limiting
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/");
    }
}
