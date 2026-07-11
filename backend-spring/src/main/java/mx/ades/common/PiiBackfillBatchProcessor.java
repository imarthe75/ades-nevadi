package mx.ades.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Procesa un lote del backfill de cifrado PII en su propia transacción.
 * Extraído de {@link PiiBackfillRunner} — necesita ser un bean separado (no un
 * método privado auto-invocado) para que {@code @Transactional} funcione: la
 * auto-invocación (`this.metodo()`) dentro de la misma clase no pasa por el
 * proxy AOP de Spring, así que un `@Transactional` puesto directamente en un
 * método privado de {@code PiiBackfillRunner} nunca se activaría (el mismo bug
 * que tenía este backfill originalmente). Un bean inyectado sí pasa por el proxy.
 *
 * <p>Cada lote es una transacción independiente (no todo el backfill en una
 * sola transacción gigante) — mantiene el tamaño de transacción acotado y
 * permite que el progreso ya hecho sobrevida si un lote posterior falla.
 */
@Service
public class PiiBackfillBatchProcessor {

    static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final PiiEncryptionService pii;

    public PiiBackfillBatchProcessor(JdbcTemplate jdbc, PiiEncryptionService pii) {
        this.jdbc = jdbc;
        this.pii = pii;
    }

    @Transactional
    public int procesarLote() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, curp, telefono, email_personal
              FROM ades_personas
             WHERE pii_encryption_status = 'pending'
             ORDER BY fecha_creacion
             LIMIT ?
            """, BATCH_SIZE);

        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String curp = (String) row.get("curp");
            String telefono = (String) row.get("telefono");
            String email = (String) row.get("email_personal");
            jdbc.update("""
                UPDATE ades_personas
                   SET curp_encrypted = ?, curp_hash = ?,
                       telefono_encrypted = ?, telefono_hash = ?,
                       email_personal_encrypted = ?, email_personal_hash = ?,
                       pii_encryption_status = 'completado'
                 WHERE id = ?
                """,
                pii.encrypt(curp), pii.hash(curp),
                pii.encrypt(telefono), pii.hash(telefono),
                pii.encrypt(email), pii.hash(email),
                id);
        }
        return rows.size();
    }
}
