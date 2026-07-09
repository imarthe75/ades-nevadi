package mx.ades.config;

import io.bucket4j.Bandwidth;
import io.bucket4j.Bucket;
import io.bucket4j.Bucket4j;
import io.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Configuration usando Bucket4j
 * Protege contra:
 * - Brute force attacks en login (5 req/min/IP)
 * - API abuse / credential stuffing (100 req/min/IP)
 * - DDoS amplification
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Rate limiter para /api/v1/auth/** — 5 intentos por minuto por IP
     */
    @Bean
    public RateLimiter authRateLimiter() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return new RateLimiter("auth", limit);
    }

    /**
     * Rate limiter para /api/v1/** — 100 intentos por minuto por IP
     */
    @Bean
    public RateLimiter apiRateLimiter() {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return new RateLimiter("api", limit);
    }

    /**
     * Helper class para gestionar rate limiting con Bucket4j
     * Thread-safe usando ConcurrentHashMap
     */
    public static class RateLimiter {
        private final String name;
        private final Bandwidth bandwidth;
        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

        public RateLimiter(String name, Bandwidth bandwidth) {
            this.name = name;
            this.bandwidth = bandwidth;
        }

        /**
         * Intenta consumir un token. Retorna true si permitido, false si rate limit excedido.
         */
        public boolean tryConsume(String key) {
            Bucket bucket = buckets.computeIfAbsent(key, k ->
                Bucket4j.builder().addLimit(bandwidth).build());
            return bucket.tryConsume(1);
        }

        /**
         * Obtiene tokens restantes para una clave
         */
        public long getRemainingTokens(String key) {
            Bucket bucket = buckets.get(key);
            if (bucket == null) return bandwidth.getCapacity();
            return bucket.getAvailableTokens();
        }

        public String getName() {
            return name;
        }
    }
}
