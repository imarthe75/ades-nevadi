package mx.ades.modules.learning_paths.application.service;

import mx.ades.modules.learning_paths.domain.model.EstatusAsignacion;
import mx.ades.modules.learning_paths.domain.port.in.RegistrarProgresoUseCase;
import mx.ades.modules.learning_paths.domain.port.out.LearningPathRepositoryPort;

public class LearningPathApplicationService implements RegistrarProgresoUseCase {

    private final LearningPathRepositoryPort repo;

    public LearningPathApplicationService(LearningPathRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Result ejecutar(Command command) {
        repo.upsertProgreso(command.asignacionId(), command.recursoId(),
                command.tiempoMin(), command.calificacion());

        LearningPathRepositoryPort.ProgresoStats stats = repo.calcularProgreso(command.asignacionId());

        EstatusAsignacion nuevoEstatus = EstatusAsignacion.PENDIENTE
                .transicion(stats.totalCompletados(), stats.totalObligatorios());

        repo.actualizarEstatus(command.asignacionId(), nuevoEstatus.name(), stats.pctCompletado());

        return new Result(nuevoEstatus.name(), stats.pctCompletado());
    }
}
