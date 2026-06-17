package mx.ades.modules.boletas.domain.port.in;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

public interface GenerarBoletaUseCase {

    ResponseEntity<byte[]> generar(UUID estudianteId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> encolarGrupo(UUID grupoId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> estadoTarea(String taskId, String authHeader);
}
