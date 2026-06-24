package mx.ades.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de caché de Spring para el BFF de ADES.
 * <p>
 * Habilita la infraestructura de caché declarativa ({@code @Cacheable},
 * {@code @CacheEvict}, etc.) mediante {@link org.springframework.cache.annotation.EnableCaching}.
 * El proveedor concreto (Valkey/Redis) se configura vía propiedades
 * {@code spring.data.redis.*} en {@code application.properties}.
 * </p>
 * <p>
 * Los catálogos de larga vida (niveles educativos, grados, ciclos escolares) son
 * candidatos naturales para cachear y reducir roundtrips a PostgreSQL.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
