package mx.ades.modules.cierre.domain.port.in;

import java.util.UUID;

public interface CerrarCicloUseCase {

    record Command(UUID cicloId, UUID cicloDestinoId, Integer nivelAcceso, String usuario) {
        public Command {
            if (cicloId == null) throw new IllegalArgumentException("ciclo_id es requerido");
            if (nivelAcceso == null || nivelAcceso > 2)
                throw new IllegalArgumentException("Solo administradores o directores pueden realizar el cierre de ciclo");
        }
    }

    String cerrar(Command cmd);
}
