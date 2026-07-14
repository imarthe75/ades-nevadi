package mx.ades.modules.certificados.infrastructure.outbound.http;

import mx.ades.modules.certificados.domain.port.out.CertificadoFastApiPort;
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
 * Adaptador de salida HTTP que implementa {@link CertificadoFastApiPort} proxeando
 * las solicitudes de certificados al servicio FastAPI en
 * {@code http://ades-api:8000/api/v1/certificados} usando {@code RestClient}.
 *
 * <p>Propaga headers de respuesta del FastAPI ({@code X-Folio}, {@code X-Certificado-Id},
 * {@code X-Estado-Firma}) para que el cliente los pueda leer directamente.</p>
 *
 * @author ADES
 * @since 2026
 */
@Component
public class CertificadoFastApiAdapter implements CertificadoFastApiPort {

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/certificados";

    private final RestClient restClient = RestClient.builder().build();

    @Override
    public ResponseEntity<byte[]> emitir(Map<String, Object> body, String authHeader) {
        try {
            RestClient.RequestBodySpec req = restClient.post()
                    .uri(API_BASE_URL + "/emitir")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);

            ResponseEntity<byte[]> resp = req.retrieve().toEntity(byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            copyHeader(resp.getHeaders(), headers, HttpHeaders.CONTENT_DISPOSITION);
            copyHeader(resp.getHeaders(), headers, "X-Folio");
            copyHeader(resp.getHeaders(), headers, "X-Certificado-Id");
            copyHeader(resp.getHeaders(), headers, "X-Estado-Firma");
            return new ResponseEntity<>(resp.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw traducirError(e, "Error al emitir certificado");
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> firmar(UUID certId, String authHeader) {
        try {
            RestClient.RequestBodySpec req = restClient.post()
                    .uri(API_BASE_URL + "/" + certId + "/firmar");
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw traducirError(e, "Error al firmar certificado");
        }
    }

    @Override
    public Map<String, Object> verificar(String folio) {
        try {
            return restClient.get()
                    .uri(API_BASE_URL + "/verificar/" + folio)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw traducirError(e, "Error al verificar certificado");
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> generarLlave(String authHeader) {
        try {
            RestClient.RequestBodySpec req = restClient.post().uri(API_BASE_URL + "/llave/generar");
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw traducirError(e, "Error al generar llave");
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> registrarLlave(Map<String, Object> body, String authHeader) {
        try {
            RestClient.RequestBodySpec req = restClient.post()
                    .uri(API_BASE_URL + "/llave/registrar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw traducirError(e, "Error al registrar llave");
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> obtenerLlaveActiva(String authHeader) {
        try {
            RestClient.RequestHeadersSpec<?> req = restClient.get().uri(API_BASE_URL + "/llave/activa");
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw traducirError(e, "Error al obtener llave activa");
        }
    }

    private void copyHeader(HttpHeaders source, HttpHeaders target, String name) {
        String value = source.getFirst(name);
        if (value != null) target.set(name, value);
    }

    /**
     * Traduce excepciones de RestClient preservando el status real de FastAPI
     * (ej. 404/400 legítimos) en vez de colapsar todo a 502 — antes un folio
     * inexistente respondía "Bad Gateway" en vez de 404.
     */
    private ResponseStatusException traducirError(Exception e, String contexto) {
        if (e instanceof org.springframework.web.client.RestClientResponseException rce) {
            return new ResponseStatusException(HttpStatus.valueOf(rce.getStatusCode().value()), rce.getResponseBodyAsString());
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, contexto + ": " + e.getMessage());
    }
}
