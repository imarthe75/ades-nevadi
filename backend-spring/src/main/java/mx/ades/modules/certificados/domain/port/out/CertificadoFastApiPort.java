package mx.ades.modules.certificados.domain.port.out;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de comunicación HTTP con el servicio FastAPI
 * encargado de emitir y firmar certificados escolares con firma digital Ed25519.
 *
 * <p>Spring solo actúa como proxy; la lógica de firma, generación PDF y verificación
 * pública por folio reside en el backend FastAPI ({@code certificados.py}).</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CertificadoFastApiPort {

    ResponseEntity<byte[]> emitir(Map<String, Object> body, String authHeader);

    ResponseEntity<Map<String, Object>> firmar(UUID certId, String authHeader);

    Map<String, Object> verificar(String folio);

    ResponseEntity<Map<String, Object>> generarLlave(String authHeader);

    ResponseEntity<Map<String, Object>> registrarLlave(Map<String, Object> body, String authHeader);

    ResponseEntity<Map<String, Object>> obtenerLlaveActiva(String authHeader);
}
