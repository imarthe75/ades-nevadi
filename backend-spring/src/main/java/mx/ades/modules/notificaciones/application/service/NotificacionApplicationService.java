package mx.ades.modules.notificaciones.application.service;

import mx.ades.modules.notificaciones.domain.port.in.MarcarLeidaUseCase;
import mx.ades.modules.notificaciones.domain.port.in.MarcarTodasLeidasUseCase;
import mx.ades.modules.notificaciones.domain.port.out.NotificacionWriteRepositoryPort;

public class NotificacionApplicationService implements MarcarLeidaUseCase, MarcarTodasLeidasUseCase {

    private final NotificacionWriteRepositoryPort repository;

    public NotificacionApplicationService(NotificacionWriteRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public void marcar(MarcarLeidaUseCase.Command cmd) {
        repository.marcarLeida(cmd.notifId(), cmd.usuarioId());
    }

    @Override
    public void marcarTodas(MarcarTodasLeidasUseCase.Command cmd) {
        repository.marcarTodasLeidas(cmd.usuarioId());
    }
}
