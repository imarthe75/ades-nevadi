package mx.ades.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Servicio de notificaciones push en tiempo real hacia usuarios del sistema ADES,
 * utilizando <a href="https://ntfy.sh">ntfy</a> (contenedor {@code ades-ntfy})
 * como broker de mensajería.
 * <p>
 * Cada usuario recibe mensajes en su topic personal {@code ades_<usuarioId>},
 * lo que garantiza aislamiento entre destinatarios. Soporta envío síncrono,
 * asíncrono y por lotes. El envío asíncrono no bloquea el hilo del request HTTP.
 * </p>
 * <p>
 * Si {@code ntfy.url} no está configurado, las llamadas retornan {@code false}
 * silenciosamente para no interrumpir flujos principales.
 * </p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@Slf4j
public class PushService {

    private final RestClient restClient;

    @Value("${ntfy.url:http://ades-ntfy:80}")
    private String ntfyUrl;

    @Value("${ntfy.admin-token:}")
    private String adminToken;

    public PushService() {
        this.restClient = RestClient.builder().build();
    }

    public boolean send(UUID usuarioId, String titulo, String mensaje, String prioridad, List<String> tags, String url) {
        if (ntfyUrl == null || ntfyUrl.isBlank()) {
            return false;
        }

        String topic = "ades_" + usuarioId;
        String requestUrl = ntfyUrl.endsWith("/") ? ntfyUrl + topic : ntfyUrl + "/" + topic;

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(requestUrl)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(mensaje);

            spec.header("Title", titulo);
            if (prioridad != null) {
                spec.header("Priority", prioridad);
            }
            if (tags != null && !tags.isEmpty()) {
                spec.header("Tags", String.join(",", tags));
            }
            if (url != null) {
                spec.header("Click", url);
            }
            if (adminToken != null && !adminToken.isBlank()) {
                spec.header("Authorization", "Bearer " + adminToken);
            }

            ResponseEntity<String> response = spec.retrieve().toEntity(String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to send push notification to {}: {}", usuarioId, e.getMessage());
            return false;
        }
    }

    @Async
    public void sendAsync(UUID usuarioId, String titulo, String mensaje, String prioridad, List<String> tags, String url) {
        send(usuarioId, titulo, mensaje, prioridad, tags, url);
    }

    @Async
    public void sendBatchAsync(List<UUID> usuarioIds, String titulo, String mensaje, String prioridad, List<String> tags, String url) {
        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return;
        }
        for (UUID uid : usuarioIds) {
            send(uid, titulo, mensaje, prioridad, tags, url);
        }
    }
}
