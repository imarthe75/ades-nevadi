package mx.ades.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

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
