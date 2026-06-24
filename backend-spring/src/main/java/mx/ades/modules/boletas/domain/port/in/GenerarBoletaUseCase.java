package mx.ades.modules.boletas.domain.port.in;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para la generación de boletas de calificaciones
 * en el dominio de boletas.
 *
 * <p>El BFF Spring proxea la solicitud al servicio FastAPI (WeasyPrint + Jinja2),
 * que genera el PDF con la boleta NEM (escala SEP 6-10) o la constancia UAEMEX
 * (escala 0-10, RGEMS). También expone encolado asíncrono por grupo y consulta de tarea.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface GenerarBoletaUseCase {

    ResponseEntity<byte[]> generar(UUID estudianteId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> encolarGrupo(UUID grupoId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> estadoTarea(String taskId, String authHeader);

    ResponseEntity<byte[]> generarUaemex(UUID estudianteId, UUID cicloId, String authHeader);
}
