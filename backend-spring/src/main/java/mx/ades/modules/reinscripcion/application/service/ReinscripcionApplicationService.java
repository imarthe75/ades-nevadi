package mx.ades.modules.reinscripcion.application.service;

import mx.ades.modules.reinscripcion.domain.event.ReinscripcionProcesadaEvent;
import mx.ades.modules.reinscripcion.domain.port.in.ProcesarAccionReinscripcionUseCase;
import mx.ades.modules.reinscripcion.domain.port.out.ReinscripcionRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

public class ReinscripcionApplicationService implements ProcesarAccionReinscripcionUseCase {

    private final ReinscripcionRepositoryPort repo;
    private final ApplicationEventPublisher events;

    public ReinscripcionApplicationService(ReinscripcionRepositoryPort repo,
                                           ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Override
    public Result ejecutar(Command cmd) {
        String estado = cmd.accion().toEstado();
        repo.procesarAccion(cmd.registroId(), estado, cmd.razonRechazo(), cmd.procesadoPor());

        events.publishEvent(new ReinscripcionProcesadaEvent(
                cmd.registroId(), cmd.accion(), estado, cmd.procesadoPor(), Instant.now()));

        return new Result(cmd.registroId(), estado);
    }
}
