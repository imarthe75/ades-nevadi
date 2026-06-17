package mx.ades.modules.boletas.domain.port.out;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

public interface BoletaFastApiPort {

    ResponseEntity<byte[]> generar(UUID estudianteId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> encolarGrupo(UUID grupoId, UUID cicloId, String authHeader);

    ResponseEntity<Map<String, Object>> estadoTarea(String taskId, String authHeader);
}
