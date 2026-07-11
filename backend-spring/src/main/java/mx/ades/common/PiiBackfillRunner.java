package mx.ades.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
 *
 * <p>El procesamiento real vive en {@link PiiBackfillBatchProcessor} (bean
 * separado, no un método propio) — ver el javadoc de esa clase para el motivo:
 * este runner llamaba antes a un método privado auto-invocado, lo que hacía
 * que un {@code @Transactional} ahí nunca se activara (bug ya corregido).
 */
@Component
@ConditionalOnProperty(prefix = "ades.pii", name = "backfill-run", havingValue = "true")
public class PiiBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PiiBackfillRunner.class);

    private final PiiEncryptionService pii;
    private final PiiBackfillBatchProcessor batchProcessor;

    public PiiBackfillRunner(PiiEncryptionService pii, PiiBackfillBatchProcessor batchProcessor) {
        this.pii = pii;
        this.batchProcessor = batchProcessor;
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
            lote = batchProcessor.procesarLote();
            totalProcesadas += lote;
            if (lote > 0) log.info("[PII-BACKFILL] Lote procesado: {} filas (acumulado: {})", lote, totalProcesadas);
        } while (lote == PiiBackfillBatchProcessor.BATCH_SIZE);
        log.info("[PII-BACKFILL] Completado. Total de filas procesadas: {}", totalProcesadas);
    }
}
