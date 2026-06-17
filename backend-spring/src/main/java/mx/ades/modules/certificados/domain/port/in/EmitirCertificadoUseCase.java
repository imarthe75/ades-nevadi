package mx.ades.modules.certificados.domain.port.in;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

public interface EmitirCertificadoUseCase {

    ResponseEntity<byte[]> emitir(Map<String, Object> body, String authHeader);

    ResponseEntity<Map<String, Object>> firmar(UUID certId, String authHeader);

    ResponseEntity<Map<String, Object>> generarLlave(String authHeader);

    ResponseEntity<Map<String, Object>> registrarLlave(Map<String, Object> body, String authHeader);

    ResponseEntity<Map<String, Object>> obtenerLlaveActiva(String authHeader);
}
