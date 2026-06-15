package mx.ades.modules.expediente;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaperlessService {

    private final RestClient restClient = RestClient.builder().build();

    @Value("${paperless.url:http://ades-paperless:8000}")
    private String paperlessUrl;

    @Value("${paperless.api-token:}")
    private String paperlessApiToken;

    private String getAuthHeader() {
        return "Token " + paperlessApiToken;
    }

    public boolean hasToken() {
        return paperlessApiToken != null && !paperlessApiToken.isBlank();
    }

    public String subirDocumento(String nombre, byte[] contenido, String contentType, String titulo) {
        if (!hasToken()) {
            log.warn("Paperless API Token no configurado, omitiendo subida.");
            return null;
        }
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            if (titulo != null) {
                body.add("title", titulo);
            }
            ByteArrayResource fileResource = new ByteArrayResource(contenido) {
                @Override
                public String getFilename() {
                    return nombre;
                }
            };
            body.add("document", fileResource);

            ResponseEntity<String> response = restClient.post()
                    .uri(paperlessUrl + "/api/documents/post_document/")
                    .header("Authorization", getAuthHeader())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String taskId = response.getBody().trim().replace("\"", "");
                log.info("Documento subido a Paperless con éxito, task_id: {}", taskId);
                return taskId;
            }
        } catch (Exception e) {
            log.error("Error al subir documento a Paperless: {}", e.getMessage());
        }
        return null;
    }

    public byte[] descargarDocumento(Integer docId) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(paperlessUrl + "/api/documents/" + docId + "/download/")
                    .header("Authorization", getAuthHeader())
                    .retrieve()
                    .toEntity(byte[].class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Error al descargar documento {} desde Paperless: {}", docId, e.getMessage());
        }
        return null;
    }

    public boolean eliminarDocumento(Integer docId) {
        try {
            ResponseEntity<Void> response = restClient.delete()
                    .uri(paperlessUrl + "/api/documents/" + docId + "/")
                    .header("Authorization", getAuthHeader())
                    .retrieve()
                    .toBodilessEntity();

            return response.getStatusCode().is2xxSuccessful() || response.getStatusCode() == HttpStatus.NO_CONTENT;
        } catch (Exception e) {
            log.error("Error al eliminar documento {} de Paperless: {}", docId, e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buscarDocumentos(String query, int page, int pageSize) {
        try {
            String uri = paperlessUrl + "/api/documents/?page=" + page + "&page_size=" + pageSize;
            if (query != null && !query.isBlank()) {
                uri += "&query=" + query;
            }

            ResponseEntity<Map> response = restClient.get()
                    .uri(uri)
                    .header("Authorization", getAuthHeader())
                    .retrieve()
                    .toEntity(Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Error al buscar documentos en Paperless: {}", e.getMessage());
        }
        return Collections.singletonMap("results", Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerDocumento(Integer docId) {
        try {
            ResponseEntity<Map> response = restClient.get()
                    .uri(paperlessUrl + "/api/documents/" + docId + "/")
                    .header("Authorization", getAuthHeader())
                    .retrieve()
                    .toEntity(Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Error al obtener documento {} desde Paperless: {}", docId, e.getMessage());
        }
        return null;
    }
}
