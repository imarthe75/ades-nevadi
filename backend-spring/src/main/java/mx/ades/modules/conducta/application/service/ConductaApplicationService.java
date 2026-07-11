package mx.ades.modules.conducta.application.service;

import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;
import mx.ades.modules.conducta.domain.port.out.PlanMejoraRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Caso de uso: creación de planes de mejora conductual para alumnos.
 * Implementa {@link CrearPlanMejoraUseCase} coordinando el dominio de conducta
 * con el puerto de repositorio de planes de mejora, garantizando que no exista
 * más de un plan activo por reporte de conducta.
 *
 * @author ADES
 * @since 2026
 */
public class ConductaApplicationService implements CrearPlanMejoraUseCase {

    private final PlanMejoraRepositoryPort planRepo;

    public ConductaApplicationService(PlanMejoraRepositoryPort planRepo) {
        this.planRepo = planRepo;
    }

    @Override
    @Transactional
    public UUID ejecutar(Command command) {
        if (planRepo.existeActivo(command.reporteId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este reporte ya tiene un plan de mejora activo");
        }
        return planRepo.guardar(command);
    }
}
