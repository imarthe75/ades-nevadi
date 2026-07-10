package mx.ades.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de caché de Spring para el BFF de ADES.
 * <p>
 * Habilita la infraestructura de caché declarativa ({@code @Cacheable},
 * {@code @CacheEvict}, etc.) mediante {@link org.springframework.cache.annotation.EnableCaching}.
 * El proveedor concreto (Valkey/Redis) se configura vía propiedades
 * {@code spring.data.redis.*} en {@code application.properties}.
 * </p>
 * <p>
 * Cada caché declara su propio TTL: sin TTL explícito, Redis retiene las entradas
 * indefinidamente y el valor cacheado puede quedar obsoleto tras una edición
 * (ej. un temario o una rúbrica modificados a media del ciclo escolar).
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();
        // Catálogos estáticos (roles, países, nacionalidades): cambian casi nunca.
        perCacheConfig.put("catalogos", defaultConfig.entryTtl(Duration.ofHours(6)));
        // Alumno por id: puede editarse durante el día (datos de contacto, expediente).
        perCacheConfig.put("alumnos", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        // Boletas ya calculadas: estables una vez cerrado el periodo.
        perCacheConfig.put("boletas", defaultConfig.entryTtl(Duration.ofHours(1)));
        // Temarios/aprendizajes esperados: fijados al inicio de ciclo, edición ocasional.
        perCacheConfig.put("temarios", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // Reporte 911: cómputo pesado, se genera para transcripción puntual (no tiempo real).
        perCacheConfig.put("reportes911", defaultConfig.entryTtl(Duration.ofHours(1)));
        // Rúbricas de evaluación: catálogo de uso frecuente, edición poco común.
        perCacheConfig.put("rubricas", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // Kardex UAEMEX: cómputo pesado (ordinario→extraordinario→definitiva), consulta puntual.
        perCacheConfig.put("kardex", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // Analítica de calificaciones (tendencias, riesgo, cobertura): agregados pesados, no tiempo real.
        perCacheConfig.put("analytics", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(perCacheConfig)
            .build();
    }
}
