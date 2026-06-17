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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al emitir certificado: " + e.getMessage());
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al firmar certificado: " + e.getMessage());
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al verificar certificado: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> generarLlave(String authHeader) {
        try {
            RestClient.RequestBodySpec req = restClient.post().uri(API_BASE_URL + "/llave/generar");
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al generar llave: " + e.getMessage());
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al registrar llave: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> obtenerLlaveActiva(String authHeader) {
        try {
            RestClient.RequestHeadersSpec<?> req = restClient.get().uri(API_BASE_URL + "/llave/activa");
            if (authHeader != null) req.header(HttpHeaders.AUTHORIZATION, authHeader);
            return ResponseEntity.ok(req.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Error al obtener llave activa: " + e.getMessage());
        }
    }

    private void copyHeader(HttpHeaders source, HttpHeaders target, String name) {
        String value = source.getFirst(name);
        if (value != null) target.set(name, value);
    }
}
