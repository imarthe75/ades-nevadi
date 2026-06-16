package mx.ades.modules.procesos.application.service;

import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort.AdmisionData;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort.GrupoCapacidad;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProcesosApplicationService implements ProcesarPreinscripcionUseCase {

    private final PreinscripcionRepositoryPort repo;

    public ProcesosApplicationService(PreinscripcionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public PreinscripcionResult ejecutar(Command command) {
        AdmisionData admision = repo.findAdmisionAceptada(command.admisionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "La solicitud debe estar en estado ACEPTADO"));

        GrupoCapacidad cap = repo.findCapacidadGrupo(command.grupoId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Grupo no encontrado"));

        if (cap.estaLleno()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El grupo está lleno");
        }

        return repo.guardar(command, admision);
    }
}
