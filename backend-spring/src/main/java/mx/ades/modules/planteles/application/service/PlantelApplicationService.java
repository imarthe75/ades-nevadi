package mx.ades.modules.planteles.application.service;

import mx.ades.modules.planteles.Plantel;
import mx.ades.modules.planteles.domain.port.in.ActualizarPlantelUseCase;
import mx.ades.modules.planteles.domain.port.in.CrearPlantelUseCase;
import mx.ades.modules.planteles.domain.port.out.PlantelRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

public class PlantelApplicationService implements CrearPlantelUseCase, ActualizarPlantelUseCase {

    private final PlantelRepositoryPort repositoryPort;

    public PlantelApplicationService(PlantelRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public Map<String, Object> crear(CrearPlantelUseCase.Command cmd) {
        Plantel p = new Plantel();
        p.setNombrePlantel(cmd.nombrePlantel().trim());
        p.setEscuelaId(cmd.escuelaId());
        p.setClaveCt(cmd.claveCt());
        p.setEstatusId(cmd.estatusId());
        p.setIsActive(true);
        Plantel saved = repositoryPort.save(p);
        return Map.of("id", saved.getId(), "nombre_plantel", saved.getNombrePlantel());
    }

    @Override
    public Map<String, Object> actualizar(ActualizarPlantelUseCase.Command cmd) {
        Plantel p = repositoryPort.findById(cmd.plantelId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plantel no encontrado"));

        p.setNombrePlantel(cmd.nombrePlantel().trim());
        if (cmd.escuelaId() != null) p.setEscuelaId(cmd.escuelaId());
        if (cmd.claveCt() != null) p.setClaveCt(cmd.claveCt());
        if (cmd.estatusId() != null) p.setEstatusId(cmd.estatusId());
        if (cmd.isActive() != null) p.setIsActive(cmd.isActive());
        repositoryPort.save(p);
        return Map.of("updated", true);
    }
}
