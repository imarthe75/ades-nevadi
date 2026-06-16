package mx.ades.modules.notificaciones.infrastructure.outbound.persistence;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.notificaciones.domain.port.out.NotificacionWriteRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificacionWritePersistenceAdapter implements NotificacionWriteRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public void marcarLeida(UUID notifId, UUID usuarioId) {
        jdbc.update("UPDATE ades_notificaciones_sistema SET leido = TRUE WHERE id = ? AND usuario_id = ?",
                notifId, usuarioId);
    }

    @Override
    public void marcarTodasLeidas(UUID usuarioId) {
        jdbc.update("UPDATE ades_notificaciones_sistema SET leido = TRUE WHERE usuario_id = ? AND leido = FALSE",
                usuarioId);
    }
}
