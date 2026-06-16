package mx.ades.modules.notificaciones.domain.port.in;

import java.util.UUID;

public interface MarcarTodasLeidasUseCase {

    record Command(UUID usuarioId) {
        public Command {
            if (usuarioId == null) throw new IllegalArgumentException("usuario_id es requerido");
        }
    }

    void marcarTodas(Command cmd);
}
