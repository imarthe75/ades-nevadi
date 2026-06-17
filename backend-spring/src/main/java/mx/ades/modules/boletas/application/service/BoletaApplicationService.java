package mx.ades.modules.boletas.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.boletas.domain.port.in.GenerarBoletaUseCase;
import mx.ades.modules.boletas.domain.port.out.BoletaFastApiPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

@Service
public class BoletaApplicationService implements GenerarBoletaUseCase {

    private final BoletaFastApiPort fastApiPort;

    public BoletaApplicationService(BoletaFastApiPort fastApiPort) {
        this.fastApiPort = fastApiPort;
    }

    @Override
    public ResponseEntity<byte[]> generar(UUID estudianteId, UUID cicloId, String authHeader) {
        return fastApiPort.generar(estudianteId, cicloId, authHeader);
    }

    @Override
    public ResponseEntity<Map<String, Object>> encolarGrupo(UUID grupoId, UUID cicloId, String authHeader) {
        return fastApiPort.encolarGrupo(grupoId, cicloId, authHeader);
    }

    @Override
    public ResponseEntity<Map<String, Object>> estadoTarea(String taskId, String authHeader) {
        return fastApiPort.estadoTarea(taskId, authHeader);
    }
}
