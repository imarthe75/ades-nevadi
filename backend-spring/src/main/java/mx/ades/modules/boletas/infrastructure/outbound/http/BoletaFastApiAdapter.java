package mx.ades.modules.boletas.infrastructure.outbound.http;

import mx.ades.modules.boletas.domain.port.out.BoletaFastApiPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Component
public class BoletaFastApiAdapter implements BoletaFastApiPort {

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/boletas";

    private final RestClient restClient = RestClient.builder().build();

    @Override
    public ResponseEntity<byte[]> generar(UUID estudianteId, UUID cicloId, String authHeader) {
        try {
            String url = API_BASE_URL + "/" + estudianteId;
            if (cicloId != null) url += "?ciclo_id=" + cicloId;

            RestClient.RequestHeadersSpec<?> req = restClient.get().uri(url);
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);

            ResponseEntity<byte[]> resp = req.retrieve().toEntity(byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String cd = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (cd != null) headers.set(HttpHeaders.CONTENT_DISPOSITION, cd);
            return new ResponseEntity<>(resp.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al generar boleta: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> encolarGrupo(UUID grupoId, UUID cicloId, String authHeader) {
        try {
            String url = API_BASE_URL + "/grupo/" + grupoId + "/batch";
            if (cicloId != null) url += "?ciclo_id=" + cicloId;

            RestClient.RequestBodySpec req = restClient.post().uri(url);
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al encolar boletas: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> estadoTarea(String taskId, String authHeader) {
        try {
            RestClient.RequestHeadersSpec<?> req = restClient.get()
                    .uri(API_BASE_URL + "/tarea/" + taskId);
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al consultar tarea: " + e.getMessage());
        }
    }
}
