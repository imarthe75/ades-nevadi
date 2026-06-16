package mx.ades.modules.capacitaciones.domain.port.in;

import java.util.UUID;

public interface ValidarCapacitacionUseCase {

    record Command(UUID capacitacionId, UUID usuarioId, String usuarioNombre, int nivelAcceso) {
        public Command {
            if (capacitacionId == null)
                throw new IllegalArgumentException("capacitacion_id es requerido");
        }
    }

    void validar(Command cmd);
}
