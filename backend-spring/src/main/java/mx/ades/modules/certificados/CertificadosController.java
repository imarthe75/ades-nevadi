package mx.ades.modules.certificados;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.certificados.domain.port.in.EmitirCertificadoUseCase;
import mx.ades.modules.certificados.domain.port.out.CertificadoFastApiPort;
import mx.ades.modules.certificados.query.CertificadoQueryService;
import mx.ades.security.AdesUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
        return emitirUseCase.emitir(body, authHeader);
    }

    @PostMapping("/{cert_id}/firmar")
    public ResponseEntity<Map<String, Object>> firmarCertificado(
            @PathVariable("cert_id") UUID certId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
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
        userService.resolveUser(jwt);
        return emitirUseCase.generarLlave(authHeader);
    }

    @PostMapping("/llave/registrar")
    public ResponseEntity<Map<String, Object>> registrarLlave(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return emitirUseCase.registrarLlave(body, authHeader);
    }

    @GetMapping("/llave/activa")
    public ResponseEntity<Map<String, Object>> obtenerLlaveActiva(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resolveUser(jwt);
        return emitirUseCase.obtenerLlaveActiva(authHeader);
    }
}
