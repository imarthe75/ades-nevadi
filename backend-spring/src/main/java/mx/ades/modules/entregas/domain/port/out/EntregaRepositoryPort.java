package mx.ades.modules.entregas.domain.port.out;

import mx.ades.modules.entregas.domain.port.in.CalificarEntregaUseCase;
import mx.ades.modules.entregas.domain.port.in.SubirEntregaUseCase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface EntregaRepositoryPort {
    List<Map<String, Object>> listByAlumno(UUID alumnoId, UUID periodoId, UUID materiaId, boolean soloPendientes);
    List<Map<String, Object>> pendientesByGrupo(UUID grupoId, UUID materiaId);
    void upsertEntrega(SubirEntregaUseCase.Command cmd);
    int calificar(CalificarEntregaUseCase.Command cmd);
    int registrarExcusa(UUID entregaId, String motivo, String usuario);
}
