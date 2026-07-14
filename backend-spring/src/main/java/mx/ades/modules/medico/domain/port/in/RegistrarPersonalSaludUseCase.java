package mx.ades.modules.medico.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface RegistrarPersonalSaludUseCase {

    record Command(UUID plantelId, Map<String, Object> persona, Map<String, Object> laborales, String usuario) {
        public Command {
            if (plantelId == null) throw new IllegalArgumentException("plantel_id es requerido");
            if (persona == null) throw new IllegalArgumentException("persona es requerido");
            if (laborales == null) throw new IllegalArgumentException("laborales es requerido");
            // ades_personas.nombre / apellido_paterno son NOT NULL sin default; sin este chequeo
            // el INSERT de PersonalSaludPersistenceAdapter fallaba con
            // DataIntegrityViolationException -> 409 genérico en vez de un mensaje claro.
            Object nombre = persona.get("nombre");
            if (nombre == null || nombre.toString().isBlank())
                throw new IllegalArgumentException("nombre es requerido");
            Object apellidoPaterno = persona.get("apellido_paterno");
            if (apellidoPaterno == null || apellidoPaterno.toString().isBlank())
                throw new IllegalArgumentException("apellido_paterno es requerido");
        }
    }

    UUID registrar(Command cmd);
}
