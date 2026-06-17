package mx.ades.modules.profesores.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.profesores.Profesor;
import mx.ades.modules.profesores.ProfesorLaboralesService;
import mx.ades.modules.profesores.domain.port.in.ActualizarProfesorUseCase;
import mx.ades.modules.profesores.domain.port.in.CrearProfesorUseCase;
import mx.ades.modules.profesores.domain.port.out.ProfesorRepositoryPort;
import mx.ades.modules.profesores.query.ProfesorQueryService;
import mx.ades.shared.persona.PersonaUpdateHelper;

import java.util.Map;
import java.util.UUID;

@Service
public class ProfesorApplicationService implements CrearProfesorUseCase, ActualizarProfesorUseCase {

    private final ProfesorRepositoryPort  repositoryPort;
    private final PersonaUpdateHelper     personaHelper;
    private final ProfesorLaboralesService laboralesService;
    private final ProfesorQueryService    queryService;

    public ProfesorApplicationService(
            ProfesorRepositoryPort repositoryPort,
            PersonaUpdateHelper personaHelper,
            ProfesorLaboralesService laboralesService,
            ProfesorQueryService queryService) {
        this.repositoryPort  = repositoryPort;
        this.personaHelper   = personaHelper;
        this.laboralesService = laboralesService;
        this.queryService    = queryService;
    }

    @Override
    public Profesor crear(CrearProfesorUseCase.Command cmd) {
        Profesor prof = new Profesor();
        prof.setPersonaId(cmd.personaId());
        prof.setPlantelId(cmd.plantelId());
        prof.setNumeroEmpleado(cmd.numeroEmpleado());
        prof.setTipoContrato(cmd.tipoContrato() != null ? cmd.tipoContrato() : "BASE");
        prof.setIsActive(true);
        return repositoryPort.save(prof);
    }

    @Override
    public Map<String, Object> actualizar(ActualizarProfesorUseCase.Command cmd) {
        UUID personaId = queryService.resolverPersonaId(cmd.profesorId());
        personaHelper.actualizarBasico(personaId, cmd.persona());
        laboralesService.actualizar(cmd.profesorId(), cmd.laborales());
        return Map.of("updated", true);
    }
}
