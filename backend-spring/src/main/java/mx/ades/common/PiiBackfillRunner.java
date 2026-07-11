package mx.ades.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backfill one-shot de cifrado PII (LFPDPPP) para las filas de {@code ades_personas}
 * que quedaron en {@code pii_encryption_status = 'pending'} desde la migración
 * 045_encrypt_pii.sql (columnas *_encrypted/*_hash preparadas pero nunca pobladas).
 *
 * <p>Solo se activa con {@code ades.pii.backfill-run=true} (var {@code PII_BACKFILL_RUN}).
 * Idempotente: solo procesa filas con status {@code pending}, así que dejar la
 * variable en {@code true} de forma permanente no re-procesa nada ya hecho — pero
 * el default es {@code false} para no correr en cada arranque sin necesidad.
 *
 * <p>NO modifica ni borra las columnas en texto plano (curp, rfc, telefono,
 * email_personal) — solo puebla las columnas cifradas como capa adicional de
 * protección en reposo. Retirar el texto plano es una migración de mayor
 * alcance (requiere actualizar cada ruta de lectura del sistema) y queda fuera
 * de este backfill.
 */
@Component
@ConditionalOnProperty(prefix = "ades.pii", name = "backfill-run", havingValue = "true")
public class PiiBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PiiBackfillRunner.class);
    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final PiiEncryptionService pii;

    public PiiBackfillRunner(JdbcTemplate jdbc, PiiEncryptionService pii) {
        this.jdbc = jdbc;
        this.pii = pii;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!pii.isConfigured()) {
            log.error("[PII-BACKFILL] PII_ENCRYPTION_KEY no configurada — backfill abortado.");
            return;
        }
        log.info("[PII-BACKFILL] Iniciando backfill de cifrado PII en ades_personas...");
        int totalProcesadas = 0;
        int lote;
        do {
            lote = procesarLote();
            totalProcesadas += lote;
            if (lote > 0) log.info("[PII-BACKFILL] Lote procesado: {} filas (acumulado: {})", lote, totalProcesadas);
        } while (lote == BATCH_SIZE);
        log.info("[PII-BACKFILL] Completado. Total de filas procesadas: {}", totalProcesadas);
    }

    private int procesarLote() {
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
