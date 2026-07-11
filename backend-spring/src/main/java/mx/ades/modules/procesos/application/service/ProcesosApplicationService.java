package mx.ades.modules.procesos.application.service;

import mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort.AdmisionData;
import mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort.GrupoCapacidad;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Caso de uso: procesamiento de preinscripciones y conversión a inscripciones formales.
 * Implementa {@link ProcesarPreinscripcionUseCase} coordinando el dominio de procesos
 * de admisión con el puerto de repositorio, validando que la solicitud esté aceptada
 * y que el grupo destino tenga capacidad disponible antes de formalizar la inscripción.
 *
 * @author ADES
 * @since 2026
 */
public class ProcesosApplicationService implements ProcesarPreinscripcionUseCase {

    private final PreinscripcionRepositoryPort repo;

    public ProcesosApplicationService(PreinscripcionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
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
