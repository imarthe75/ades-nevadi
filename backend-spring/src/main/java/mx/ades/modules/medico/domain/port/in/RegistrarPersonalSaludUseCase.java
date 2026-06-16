package mx.ades.modules.medico.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface RegistrarPersonalSaludUseCase {

    record Command(UUID plantelId, Map<String, Object> persona, Map<String, Object> laborales, String usuario) {
        public Command {
            if (plantelId == null) throw new IllegalArgumentException("plantel_id es requerido");
            if (persona == null) throw new IllegalArgumentException("persona es requerido");
            if (laborales == null) throw new IllegalArgumentException("laborales es requerido");
        }
    }

    UUID registrar(Command cmd);
}
