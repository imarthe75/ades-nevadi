package mx.ades.modules.boletas.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.boletas.domain.port.in.GenerarBoletaUseCase;
import mx.ades.modules.boletas.domain.port.out.BoletaFastApiPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: generación de boletas de calificaciones en PDF.
 * Implementa {@link GenerarBoletaUseCase} actuando como proxy hacia FastAPI,
 * que renderiza las boletas vía WeasyPrint/Jinja2. Soporta boleta NEM
 * (SEP Primaria/Secundaria, escala 6-10 numérica y cualitativa A/B/C/D)
 * y boleta UAEMEX (Preparatoria CBU, escala 0-10, ordinario/extraordinario/definitiva).
 *
 * @author ADES
 * @since 2026
 */
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

    @Override
    public ResponseEntity<byte[]> generarUaemex(UUID estudianteId, UUID cicloId, String authHeader) {
        return fastApiPort.generarUaemex(estudianteId, cicloId, authHeader);
    }
}
