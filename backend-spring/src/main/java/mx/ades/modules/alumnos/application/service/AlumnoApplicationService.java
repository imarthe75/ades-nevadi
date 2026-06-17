package mx.ades.modules.alumnos.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.admin.AdminWriteService;
import mx.ades.modules.alumnos.AlumnoComplementariosService;
import mx.ades.modules.alumnos.Estudiante;
import mx.ades.modules.alumnos.domain.port.in.ActualizarAlumnoUseCase;
import mx.ades.modules.alumnos.domain.port.in.CrearAlumnoUseCase;
import mx.ades.modules.alumnos.domain.port.out.AlumnoRepositoryPort;
import mx.ades.modules.alumnos.query.AlumnoQueryService;
import mx.ades.shared.persona.PersonaUpdateHelper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class AlumnoApplicationService implements CrearAlumnoUseCase, ActualizarAlumnoUseCase {

    private final AlumnoRepositoryPort         repositoryPort;
    private final AdminWriteService            adminWrite;
    private final PersonaUpdateHelper          personaHelper;
    private final AlumnoComplementariosService complementariosService;
    private final AlumnoQueryService           queryService;

    public AlumnoApplicationService(
            AlumnoRepositoryPort repositoryPort,
            AdminWriteService adminWrite,
            PersonaUpdateHelper personaHelper,
            AlumnoComplementariosService complementariosService,
            AlumnoQueryService queryService) {
        this.repositoryPort         = repositoryPort;
        this.adminWrite             = adminWrite;
        this.personaHelper          = personaHelper;
        this.complementariosService = complementariosService;
        this.queryService           = queryService;
    }

    @Override
    public Map<String, Object> crear(CrearAlumnoUseCase.Command cmd) {
        if (repositoryPort.existeByCurp(cmd.curp()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un registro con esa CURP");

        UUID plantelId = cmd.plantelId() != null
                ? cmd.plantelId()
                : repositoryPort.resolverPrimerPlantelActivo();

        if (plantelId == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No hay planteles activos en el sistema");

        UUID personaId = adminWrite.insertPersona(
                cmd.nombre().trim(),
                cmd.apellidoPaterno().trim(),
                cmd.apellidoMaterno() != null ? cmd.apellidoMaterno().trim() : null,
                cmd.curp().toUpperCase().trim(),
                null, null);

        String matricula = repositoryPort.generarSiguienteMatricula();

        Estudiante est = new Estudiante();
        est.setPersonaId(personaId);
        est.setPlantelId(plantelId);
        est.setMatricula(matricula);
        est.setFechaIngreso(LocalDate.now());
        est.setIsActive(true);
        Estudiante saved = repositoryPort.save(est);

        return Map.of(
                "id",         saved.getId(),
                "matricula",  saved.getMatricula(),
                "persona_id", personaId,
                "plantel_id", plantelId);
    }

    @Override
    public Map<String, Object> actualizar(ActualizarAlumnoUseCase.Command cmd) {
        UUID personaId = queryService.resolverPersonaId(cmd.alumnoId());
        personaHelper.actualizar(personaId, cmd.persona());
        complementariosService.actualizar(cmd.alumnoId(), cmd.complementarios());
        return Map.of("updated", true);
    }
}
