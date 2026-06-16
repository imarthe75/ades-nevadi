package mx.ades.modules.conducta.domain.port.out;

import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;

import java.util.UUID;

public interface PlanMejoraRepositoryPort {
    boolean existeActivo(UUID reporteId);
    UUID guardar(CrearPlanMejoraUseCase.Command command);
}
