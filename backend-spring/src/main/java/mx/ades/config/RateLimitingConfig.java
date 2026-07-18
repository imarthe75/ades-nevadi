package mx.ades.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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
 * - API abuse / credential stuffing (300 req/min/IP)
 * - DDoS amplification
 *
 * Hallazgo real 2026-07-18 (ver docs/hallazgos/2026-07-18_reporte_tecnico_auditorias_profundas.md
 * §"rate limiting"): el límite "api" (100/min) se agotaba con tráfico de una sola sesión
 * de uso normal, reproducido en frío justo después de reiniciar ades-bff (no era
 * acumulación de pruebas repetidas). Una sola carga de pantalla dispara ~15 llamadas en
 * paralelo (menús, catálogos, stats, planteles, notificaciones...); navegar 5-7 pantallas
 * en un minuto de trabajo activo ya supera 100. Además, varios miembros del staff del
 * mismo plantel pueden compartir una IP saliente (NAT de la red escolar) — el límite por
 * IP en ese caso es, sin querer, un límite COMPARTIDO entre todo el personal conectado
 * desde ese plantel a la vez, no un límite por persona.
 *
 * Dos cambios, no solo el número:
 *  1) Capacidad 100→300 — cubre con margen a un usuario individual muy activo (10-15
 *     pantallas/min ≈ 150-225 llamadas) más el caso de varios usuarios compartiendo IP,
 *     sin dejar de ser un techo real: 300/min (5 req/s sostenidos) sigue muy por encima de
 *     cualquier patrón de uso humano vía UI, y suficientemente bajo para seguir cortando
 *     un cliente roto o un scraping real.
 *  2) Refill.intervally→Refill.greedy — "intervally" repone TODOS los tokens de golpe cada
 *     60s exactos: si una ráfaga legítima agota el bucket a los 5s del minuto, el cliente
 *     queda bloqueado hasta 55s más, aunque el promedio de tráfico sea perfectamente
 *     razonable. "greedy" repone continuamente (~5 tokens/s), así que una ráfaga se
 *     recupera en segundos, no esperando al siguiente minuto exacto — el motivo real por
 *     el que los 429 aparecían en ráfagas concentradas, no distribuidos.
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Rate limiter para /api/v1/auth/** (y /api/portal/auth/**) — 5 intentos por minuto
     * por IP. Sin cambios: es un control de fuerza bruta en login/recuperación de
     * contraseña, no tráfico de navegación normal — nunca se ha visto disparado por uso
     * legítimo (un usuario inicia sesión una vez, no 5+ veces por minuto).
     */
    @Bean
    public RateLimiter authRateLimiter() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return new RateLimiter("auth", limit);
    }

    /**
     * Rate limiter para /api/v1/** (y /api/portal/**) — 300 intentos por minuto por IP,
     * reposición continua (greedy). Ver justificación completa arriba.
     */
    @Bean
    public RateLimiter apiRateLimiter() {
        Bandwidth limit = Bandwidth.classic(300, Refill.greedy(300, Duration.ofMinutes(1)));
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
                Bucket.builder().addLimit(bandwidth).build());
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
