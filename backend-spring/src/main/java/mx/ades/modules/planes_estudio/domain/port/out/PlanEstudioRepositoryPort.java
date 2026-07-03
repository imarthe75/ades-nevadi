package mx.ades.modules.planes_estudio.domain.port.out;

import mx.ades.modules.planes_estudio.domain.port.in.AsignarMateriaUseCase;

import java.util.Map;
import java.util.UUID;

public interface PlanEstudioRepositoryPort {

    UUID insert(AsignarMateriaUseCase.Command cmd);

    Map<String, Object> fetchById(UUID id);

    void patchHorasSemana(UUID id, double horas);

    void patchObligatoria(UUID id, boolean esObligatoria);

    void patchOrden(UUID id, int orden);

    void patchEstadoPublicacion(UUID id, String estado);

    int softDelete(UUID id);
}
