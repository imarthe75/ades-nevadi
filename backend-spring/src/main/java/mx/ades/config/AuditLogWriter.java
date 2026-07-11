package mx.ades.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Escribe en {@code ades_audit_log} en una transacción PROPIA e independiente
 * ({@code REQUIRES_NEW}) de la transacción de negocio de la request que se está
 * auditando. Esto es deliberado, no un descuido: el registro de auditoría debe
 * confirmarse incluso si la operación de negocio falla o hace rollback (de
 * hecho {@link AuditHttpFilter} audita explícitamente los códigos 4xx), así que
 * NO debe compartir la transacción del request — debe tener la suya, separada,
 * gestionada aquí.
 */
@Service
public class AuditLogWriter {

    private final JdbcTemplate jdbc;

    public AuditLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String sub, String metodo, String entidad, String uri,
                          short status, int duracionMs) {
        jdbc.update(
            "INSERT INTO ades_audit_log " +
            "(id, nombre_usuario, accion, entidad, endpoint, metodo_http, codigo_respuesta, duracion_ms, " +
            " event_category, event_risk_level, security_outcome) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), sub, metodo, entidad, uri, metodo, status, duracionMs,
            "API_MUTATION",
            status >= 400 ? "MEDIUM" : "LOW",
            status >= 400 ? "FAILURE" : "SUCCESS");
    }
}
