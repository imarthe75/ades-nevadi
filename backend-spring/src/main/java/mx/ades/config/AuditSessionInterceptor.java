package mx.ades.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Aspecto de auditoría que propaga la identidad del usuario autenticado a la sesión
 * de PostgreSQL mediante {@code SET LOCAL app.current_user}, justo antes de cada
 * operación transaccional o de repositorio.
 * <p>
 * El valor inyectado es {@code auth.getName()} — ya resuelto por
 * {@link AdesJwtConverter} a {@code preferred_username} (con {@code sub} como respaldo),
 * la misma convención de identidad usada en el resto del código (ver
 * {@code AdesUserService#resolveUser}). Es leído por {@code auditoria.fn_auditoria_biu()}
 * (migración 138) para registrar {@code usuario_creacion}/{@code usuario_modificacion} en
 * cada fila de las 57 entidades que extienden {@code AdesAuditEntity}/{@code AdesBaseEntity}.
 * Sin este interceptor conectado al trigger, esos campos quedaban en {@code CURRENT_USER}
 * (el rol de conexión a BD, igual para toda la app) — hallazgo real corregido 2026-07-15.
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
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            String username = auth.getName();
            try {
                entityManager.createNativeQuery("SELECT set_config('app.current_user', :u, true)")
                        .setParameter("u", username)
                        .getSingleResult();
            } catch (Exception e) {
                // Ignore if not in an active transaction context
            }
        }
    }
}
