package mx.ades.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Aspecto de auditoría que propaga la identidad del usuario autenticado a la sesión
 * de PostgreSQL mediante {@code SET LOCAL app.current_user}, justo antes de cada
 * operación transaccional o de repositorio.
 * <p>
 * El valor inyectado ({@code sub} del JWT de Authentik) es leído por los triggers
 * {@code audit_biu} / {@code audit_aiud} del esquema {@code auditoria} para registrar
 * {@code usuario_creacion} y {@code usuario_modificacion} en cada fila. Sin este
 * interceptor, los campos de auditoría quedarían vacíos.
 * </p>
 * <p>
 * Se activa en métodos anotados con {@code @Transactional} y en cualquier método de
 * repositorio del paquete {@code mx.ades.modules}. Si no hay transacción activa,
 * el {@code set_config} falla silenciosamente para no interrumpir flujos de solo lectura.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Aspect
@Component
public class AuditSessionInterceptor {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "execution(* mx.ades.modules..*Repository.*(..))")
    public void setAuditUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            try {
                entityManager.createNativeQuery("SELECT set_config('app.current_user', :sub, true)")
                        .setParameter("sub", sub)
                        .getSingleResult();
            } catch (Exception e) {
                // Ignore if not in an active transaction context
            }
        }
    }
}
