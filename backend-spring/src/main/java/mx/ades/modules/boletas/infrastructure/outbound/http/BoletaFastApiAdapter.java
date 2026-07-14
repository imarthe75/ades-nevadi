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

/**
 * Adaptador de salida HTTP que implementa {@link BoletaFastApiPort} proxeando
 * las solicitudes de generación de boletas al servicio FastAPI en
 * {@code http://ades-api:8000/api/v1/boletas} usando {@code RestClient}.
 *
 * <p>Propaga el header {@code Authorization} del cliente original y reenvía
 * el PDF binario o el estado de tarea según el endpoint invocado.</p>
 *
 * @author ADES
 * @since 2026
 */
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
            throw traducirError(e, "Error al generar boleta");
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
            throw traducirError(e, "Error al encolar boletas");
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
            throw traducirError(e, "Error al consultar tarea");
        }
    }

    @Override
    public ResponseEntity<byte[]> generarUaemex(UUID estudianteId, UUID cicloId, String authHeader) {
        try {
            String url = API_BASE_URL + "/uaemex/" + estudianteId;
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
            throw traducirError(e, "Error al generar constancia UAEMEX");
        }
    }

    /**
     * Traduce excepciones de RestClient preservando el status real de FastAPI
     * (ej. 404/400 legítimos) en vez de colapsar todo a 502.
     */
    private ResponseStatusException traducirError(Exception e, String contexto) {
        if (e instanceof org.springframework.web.client.RestClientResponseException rce) {
            return new ResponseStatusException(HttpStatus.valueOf(rce.getStatusCode().value()), rce.getResponseBodyAsString());
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, contexto + ": " + e.getMessage());
    }
}
