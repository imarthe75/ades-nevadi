package mx.ades.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Servicio de despacho de webhooks salientes para integración de ADES con sistemas
 * externos (n8n, ERPs, automatizaciones institucionales).
 * <p>
 * Al recibir un {@code eventType}, consulta la tabla {@code ades_webhooks} para
 * encontrar suscriptores activos y les envía un payload JSON firmado con HMAC-SHA256
 * (header {@code X-Ades-Signature}) cuando el endpoint tiene configurado un
 * {@code secret_token}. Cada intento queda registrado en {@code ades_webhook_logs}
 * para trazabilidad y reintento manual.
 * </p>
 * <p>
 * El despacho es asíncrono ({@code @Async}) para no bloquear el hilo del caso de uso
 * que dispara el evento. El tipo comodín {@code *} en {@code event_type} suscribe
 * al endpoint a todos los eventos del sistema.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookService {

    private final JdbcTemplate jdbc;
    private final RestClient restClient = RestClient.builder().build();

    @Async
    public void dispatchWebhook(String eventType, Map<String, Object> data) {
        try {
            List<Map<String, Object>> webhooks = jdbc.queryForList(
                    "SELECT id, url, event_type, secret_token FROM ades_webhooks " +
                    "WHERE is_active = TRUE AND (event_type = ? OR event_type = '*')", eventType);

            if (webhooks.isEmpty()) {
                return;
            }

            for (Map<String, Object> w : webhooks) {
                UUID id = (UUID) w.get("id");
                String url = (String) w.get("url");
                String secretToken = (String) w.get("secret_token");

                sendRequest(id, url, eventType, data, secretToken);
            }
        } catch (Exception e) {
            log.error("Error dispatching webhooks for event {}: {}", eventType, e.getMessage());
        }
    }

    private void sendRequest(UUID webhookId, String url, String eventType, Map<String, Object> data, String secretToken) {
        String eventId = UUID.randomUUID().toString().substring(0, 16);
        long timestamp = System.currentTimeMillis() / 1000;

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("event_id", eventId);
        bodyMap.put("event_type", eventType);
        bodyMap.put("timestamp", timestamp);
        bodyMap.put("data", data);

        try {
            String bodyStr = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(bodyMap);

            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Ades-Event-Id", eventId)
                    .header("X-Ades-Event-Type", eventType)
                    .body(bodyStr);

            if (secretToken != null && !secretToken.isBlank()) {
                String signature = calculateHmacSha256(bodyStr, secretToken);
                spec.header("X-Ades-Signature", signature);
            }

            int statusCode;
            String responseBody;
            boolean success;

            try {
                ResponseEntity<String> response = spec.retrieve().toEntity(String.class);
                statusCode = response.getStatusCode().value();
                responseBody = response.getBody() != null ? response.getBody().substring(0, Math.min(response.getBody().length(), 1000)) : "";
                success = response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                statusCode = 0;
                responseBody = "Connection Error: " + e.getMessage();
                success = false;
            }

            jdbc.update("INSERT INTO ades_webhook_logs " +
                    "(webhook_id, event_type, payload, status_code, response_body, intentos, exitoso, fecha_envio) " +
                    "VALUES (?, ?, ?::jsonb, ?, ?, 1, ?, NOW())",
                    webhookId, eventType, bodyStr, statusCode, responseBody, success);

        } catch (Exception e) {
            log.warn("Failed to process webhook for URL {}: {}", url, e.getMessage());
        }
    }

    private String calculateHmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(rawHmac);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
