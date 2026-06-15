package mx.ades.modules.certificados;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;

import java.util.*;

@RestController
@RequestMapping("/api/v1/certificados")
@RequiredArgsConstructor
public class CertificadosController {

    private final AdesUserService userService;
    private final JdbcTemplate jdbc;
    private final RestClient restClient = RestClient.builder().build();

    private static final String API_BASE_URL = "http://ades-api:8000/api/v1/certificados";

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listarCertificados(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "tipo_certificado", required = false) String tipoCertificado,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);

        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.folio, c.tipo_certificado, c.nivel_educativo, " +
                "c.grado_completado, c.promedio_final, " +
                "c.fecha_emision, c.fecha_vencimiento, c.vigente, " +
                "c.estado_firma, c.fecha_firma, c.verificable_url, " +
                "c.blockchain_tx, c.blockchain_status, c.fecha_anclaje, c.blockchain_network, " +
                "p.nombre || ' ' || p.apellido_paterno || COALESCE(' ' || p.apellido_materno, '') AS nombre_alumno, " +
                "ce.nombre_ciclo " +
                "FROM ades_certificados c " +
                "JOIN ades_estudiantes est ON est.id = c.estudiante_id " +
                "JOIN ades_personas p ON p.id = est.persona_id " +
                "JOIN ades_ciclos_escolares ce ON ce.id = c.ciclo_escolar_id " +
                "WHERE c.is_active = TRUE "
        );

        List<Object> params = new ArrayList<>();
        if (estudianteId != null) {
            sql.append("AND c.estudiante_id = ? ");
            params.add(estudianteId);
        }
        if (tipoCertificado != null && !tipoCertificado.isBlank()) {
            sql.append("AND c.tipo_certificado = ? ");
            params.add(tipoCertificado);
        }

        sql.append("ORDER BY c.fecha_emision DESC LIMIT ?");
        params.add(limit);

        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), params.toArray()));
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
