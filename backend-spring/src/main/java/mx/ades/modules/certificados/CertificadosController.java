package mx.ades.modules.certificados;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.modules.certificados.query.CertificadoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/certificados")
@RequiredArgsConstructor
public class CertificadosController {

    private final AdesUserService userService;
    private final CertificadoQueryService queryService;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/certificados";

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listarCertificados(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "tipo_certificado", required = false) String tipoCertificado,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        return ResponseEntity.ok(queryService.listar(estudianteId, tipoCertificado, limit));
    }

    @PostMapping("/emitir")
    public ResponseEntity<byte[]> emitirCertificado(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(API_BASE_URL + "/emitir")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            if (response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION) != null) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
            }
            if (response.getHeaders().getFirst("X-Folio") != null) {
                headers.set("X-Folio", response.getHeaders().getFirst("X-Folio"));
            }
            if (response.getHeaders().getFirst("X-Certificado-Id") != null) {
                headers.set("X-Certificado-Id", response.getHeaders().getFirst("X-Certificado-Id"));
            }
            if (response.getHeaders().getFirst("X-Estado-Firma") != null) {
                headers.set("X-Estado-Firma", response.getHeaders().getFirst("X-Estado-Firma"));
            }

            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al emitir certificado en microservicio FastAPI: " + e.getMessage());
        }
    }

    @PostMapping("/{cert_id}/firmar")
    public ResponseEntity<Map<String, Object>> firmarCertificado(
            @PathVariable("cert_id") UUID certId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(API_BASE_URL + "/" + certId + "/firmar");
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al firmar certificado en microservicio FastAPI: " + e.getMessage());
        }
    }

    @GetMapping("/verificar/{folio}")
    public ResponseEntity<Map<String, Object>> verificarCertificadoPublico(
            @PathVariable("folio") String folio) {
        try {
            return ResponseEntity.ok(restClient.get()
                    .uri(API_BASE_URL + "/verificar/" + folio)
                    .retrieve()
                    .body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al verificar certificado en microservicio: " + e.getMessage());
        }
    }

    @PostMapping("/llave/generar")
    public ResponseEntity<Map<String, Object>> generarLlave(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(API_BASE_URL + "/llave/generar");
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al generar llave en microservicio FastAPI: " + e.getMessage());
        }
    }

    @PostMapping("/llave/registrar")
    public ResponseEntity<Map<String, Object>> registrarLlave(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(API_BASE_URL + "/llave/registrar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al registrar llave en microservicio FastAPI: " + e.getMessage());
        }
    }

    @GetMapping("/llave/activa")
    public ResponseEntity<Map<String, Object>> obtenerLlaveActiva(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(API_BASE_URL + "/llave/activa");
            if (authHeader != null) {
                request.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return ResponseEntity.ok(request.retrieve().body(Map.class));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al obtener llave activa en microservicio FastAPI: " + e.getMessage());
        }
    }
}
