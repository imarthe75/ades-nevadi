package mx.ades.modules.certificados.domain.port.in;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para la emisión y firma digital de certificados
 * escolares en el dominio de certificados.
 *
 * <p>El BFF Spring proxea al servicio FastAPI que implementa la firma Ed25519,
 * genera el PDF con WeasyPrint y expone un endpoint público de verificación por folio.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EmitirCertificadoUseCase {

    ResponseEntity<byte[]> emitir(Map<String, Object> body, String authHeader);

    ResponseEntity<Map<String, Object>> firmar(UUID certId, String authHeader);

    ResponseEntity<Map<String, Object>> generarLlave(String authHeader);

    ResponseEntity<Map<String, Object>> registrarLlave(Map<String, Object> body, String authHeader);

    ResponseEntity<Map<String, Object>> obtenerLlaveActiva(String authHeader);
}
