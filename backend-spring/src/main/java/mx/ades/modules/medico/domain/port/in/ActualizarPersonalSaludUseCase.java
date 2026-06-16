package mx.ades.modules.medico.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface ActualizarPersonalSaludUseCase {

    record Command(UUID saludId, Map<String, Object> persona, Map<String, Object> laborales, String usuario) {
        public Command {
            if (saludId == null) throw new IllegalArgumentException("salud_id es requerido");
        }
    }

    void actualizar(Command cmd);
}
