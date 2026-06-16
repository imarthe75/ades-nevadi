package mx.ades.modules.notificaciones.domain.port.out;

import java.util.UUID;

public interface NotificacionWriteRepositoryPort {
    void marcarLeida(UUID notifId, UUID usuarioId);
    void marcarTodasLeidas(UUID usuarioId);
}
