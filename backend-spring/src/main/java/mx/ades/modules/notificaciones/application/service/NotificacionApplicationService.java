package mx.ades.modules.notificaciones.application.service;

import mx.ades.modules.notificaciones.domain.port.in.MarcarLeidaUseCase;
import mx.ades.modules.notificaciones.domain.port.in.MarcarTodasLeidasUseCase;
import mx.ades.modules.notificaciones.domain.port.out.NotificacionWriteRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: marcado de notificaciones como leídas en el portal de usuarios.
 * Implementa {@link MarcarLeidaUseCase} y {@link MarcarTodasLeidasUseCase}
 * coordinando el dominio de notificaciones con el puerto de escritura de
 * repositorio para mantener el estado de lectura por usuario.
 *
 * @author ADES
 * @since 2026
 */
public class NotificacionApplicationService implements MarcarLeidaUseCase, MarcarTodasLeidasUseCase {

    private final NotificacionWriteRepositoryPort repository;

    public NotificacionApplicationService(NotificacionWriteRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void marcar(MarcarLeidaUseCase.Command cmd) {
        repository.marcarLeida(cmd.notifId(), cmd.usuarioId());
    }

    @Override
    @Transactional
    public void marcarTodas(MarcarTodasLeidasUseCase.Command cmd) {
        repository.marcarTodasLeidas(cmd.usuarioId());
    }
}
