package mx.ades.modules.boletas.domain.port.out;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de comunicación HTTP con el servicio FastAPI
 * encargado de generar los PDFs de boletas (NEM y UAEMEX) mediante WeasyPrint.
 *
 * <p>Spring solo actúa como proxy; toda la lógica de renderizado y plantillas
 * Jinja2 reside en el backend FastAPI ({@code tasks/boletas.py}).</p>
 *
 * @author ADES
 * @since 2026
 */
public interface BoletaFastApiPort {

    ResponseEntity<byte[]> generar(UUID estudianteId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> encolarGrupo(UUID grupoId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> estadoTarea(String taskId, String authHeader);

    ResponseEntity<byte[]> generarUaemex(UUID estudianteId, UUID cicloId, String authHeader);
}
