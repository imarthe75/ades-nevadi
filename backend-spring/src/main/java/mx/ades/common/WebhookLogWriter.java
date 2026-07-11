package mx.ades.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Escribe en {@code ades_webhook_logs} en una transacción propia, corta y
 * separada de la llamada HTTP al webhook externo. Deliberadamente NO se anota
 * {@code @Transactional} sobre {@link WebhookService#dispatchWebhook} completo
 * (que incluye las llamadas de red a cada endpoint suscrito): eso mantendría
 * una conexión del pool (tamaño 10) abierta durante I/O de red potencialmente
 * lento, arriesgando agotar el pool bajo carga. Aislar el INSERT aquí evita eso.
 */
@Service
public class WebhookLogWriter {

    private final JdbcTemplate jdbc;

    public WebhookLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void registrar(UUID webhookId, String eventType, String payload,
                          int statusCode, String responseBody, boolean exitoso) {
        jdbc.update("INSERT INTO ades_webhook_logs " +
                "(webhook_id, event_type, payload, status_code, response_body, intentos, exitoso, fecha_envio) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, 1, ?, NOW())",
                webhookId, eventType, payload, statusCode, responseBody, exitoso);
    }
}
