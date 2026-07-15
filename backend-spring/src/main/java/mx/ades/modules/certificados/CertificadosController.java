package mx.ades.modules.certificados;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.certificados.domain.port.in.EmitirCertificadoUseCase;
import mx.ades.modules.certificados.domain.port.out.CertificadoFastApiPort;
import mx.ades.modules.certificados.query.CertificadoQueryService;
import mx.ades.security.AdesUser;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador REST para la emisión y firma digital de certificados académicos.
 * Expone endpoints bajo /api/v1/certificados para listar, emitir (proxy a FastAPI),
 * firmar con llave Ed25519 y gestionar llaves de firma institucional. El endpoint
 * GET /verificar/{folio} es público (no requiere autenticación) y permite validar
 * la autenticidad de un certificado por su folio. La emisión y firma proxean al
 * microservicio FastAPI (CertificadoFastApiPort) que genera el PDF con WeasyPrint.
 * Toda operación protegida requiere JWT válido via {@code resolveUser}.
 *
 * @author ADES
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/certificados")
@RequiredArgsConstructor
public class CertificadosController {

    private final AdesUserService userService;
    private final CertificadoQueryService queryService;
    private final EmitirCertificadoUseCase emitirUseCase;
    private final CertificadoFastApiPort fastApiPort;

    // Nivel mínimo para emitir/firmar certificados (SECRETARIA_ACADEMICA/COORDINADOR = 3)
    private static final int NIVEL_MIN_EMISION = 3;
    // Nivel mínimo para gestionar la llave de firma Ed25519 institucional — operación
    // criptográfica crítica de todo el sistema de certificación, restringida a ADMIN.
    private static final int NIVEL_MIN_LLAVE = 1;

    /**
     * BFLA fix: emitir/firmar un certificado académico (documento oficial) no puede
     * quedar abierto a cualquier cuenta autenticada — antes de este chequeo un
     * alumno/padre (nivelAcceso &gt;=5) podía invocar directamente estos endpoints.
     * Umbral igual al usado para actas de cierre de ciclo (SECRETARIA_ACADEMICA+).
     */
    private void requireEmisor(AdesUser user) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MIN_EMISION) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere Secretaría Académica o superior");
        }
    }

    /**
     * BFLA fix: generar/registrar la llave de firma Ed25519 reemplaza la llave activa
     * que valida TODOS los certificados emitidos por la institución — antes de este
     * chequeo cualquier cuenta autenticada podía rotarla o registrar una llave externa,
     * comprometiendo la cadena de confianza de todos los certificados ya emitidos.
     * Restringido a ADMIN_GLOBAL/ADMIN_PLANTEL.
     */
    private void requireAdminLlave(AdesUser user) {
        if (user.getNivelAcceso() == null || user.getNivelAcceso() > NIVEL_MIN_LLAVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere administrador para gestionar la llave de firma");
        }
    }

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listarCertificados(
            @RequestParam(value = "estudiante_id", required = false) UUID estudianteId,
            @RequestParam(value = "tipo_certificado", required = false) String tipoCertificado,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        AdesUser user = userService.resolveUser(jwt);
        // BOLA fix: sin estudiante_id, este listado devolvía TODOS los certificados
        // emitidos por la institución (nombre, promedio, folio) a cualquier cuenta
        // autenticada. Personal escolar (nivelAcceso <=4) conserva alcance institucional;
        // alumnos/padres deben filtrar por su propio estudiante_id.
        if (user.getNivelAcceso() != null && user.getNivelAcceso() > 4 && estudianteId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Debe especificar estudiante_id");
        }
        return ResponseEntity.ok(queryService.listar(estudianteId, tipoCertificado, limit));
    }

    @PostMapping("/emitir")
    public ResponseEntity<byte[]> emitirCertificado(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        requireEmisor(userService.resolveUser(jwt));
        return emitirUseCase.emitir(body, authHeader);
    }

    @PostMapping("/{cert_id}/firmar")
    public ResponseEntity<Map<String, Object>> firmarCertificado(
            @PathVariable("cert_id") UUID certId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        requireEmisor(userService.resolveUser(jwt));
        return emitirUseCase.firmar(certId, authHeader);
    }

    @GetMapping("/verificar/{folio}")
    public ResponseEntity<Map<String, Object>> verificarCertificadoPublico(
            @PathVariable("folio") String folio) {
        return ResponseEntity.ok(fastApiPort.verificar(folio));
    }

    @PostMapping("/llave/generar")
    public ResponseEntity<Map<String, Object>> generarLlave(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdminLlave(userService.resolveUser(jwt));
        return emitirUseCase.generarLlave(authHeader);
    }

    @PostMapping("/llave/registrar")
    public ResponseEntity<Map<String, Object>> registrarLlave(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdminLlave(userService.resolveUser(jwt));
        return emitirUseCase.registrarLlave(body, authHeader);
    }

    @GetMapping("/llave/activa")
    public ResponseEntity<Map<String, Object>> obtenerLlaveActiva(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        requireEmisor(userService.resolveUser(jwt));
        return emitirUseCase.obtenerLlaveActiva(authHeader);
    }
}
