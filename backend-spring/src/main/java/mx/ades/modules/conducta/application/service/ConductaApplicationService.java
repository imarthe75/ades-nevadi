package mx.ades.modules.conducta.application.service;

import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;
import mx.ades.modules.conducta.domain.port.out.PlanMejoraRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public class ConductaApplicationService implements CrearPlanMejoraUseCase {

    private final PlanMejoraRepositoryPort planRepo;

    public ConductaApplicationService(PlanMejoraRepositoryPort planRepo) {
        this.planRepo = planRepo;
    }

    @Override
    public UUID ejecutar(Command command) {
        if (planRepo.existeActivo(command.reporteId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este reporte ya tiene un plan de mejora activo");
        }
        return planRepo.guardar(command);
    }
}
