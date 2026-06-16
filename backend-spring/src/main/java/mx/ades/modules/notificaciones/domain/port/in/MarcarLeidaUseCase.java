package mx.ades.modules.notificaciones.domain.port.in;

import java.util.UUID;

public interface MarcarLeidaUseCase {

    record Command(UUID notifId, UUID usuarioId) {
        public Command {
            if (notifId == null) throw new IllegalArgumentException("notif_id es requerido");
            if (usuarioId == null) throw new IllegalArgumentException("usuario_id es requerido");
        }
    }

    void marcar(Command cmd);
}
