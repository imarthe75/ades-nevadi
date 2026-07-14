package mx.ades.modules.personal_admin.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface RegistrarPersonalAdminUseCase {

    record Command(UUID plantelId, Map<String, Object> persona, Map<String, Object> laborales, String usuario) {
        public Command {
            if (plantelId == null) throw new IllegalArgumentException("plantel_id es requerido");
            if (persona == null) throw new IllegalArgumentException("persona es requerido");
            if (laborales == null) throw new IllegalArgumentException("laborales es requerido");
            // ades_personas.nombre / apellido_paterno son NOT NULL sin default. ValidationUtils.
            // validarPersonaMap solo valida formato (todos los campos son opcionales ahí porque
            // también se reutiliza para PATCH parciales) — aquí, en el alta, sí son obligatorios.
            // Sin este chequeo el INSERT de PersonalAdminPersistenceAdapter.createPersona fallaba
            // con DataIntegrityViolationException -> 409 genérico en vez de un mensaje claro.
            Object nombre = persona.get("nombre");
            if (nombre == null || nombre.toString().isBlank())
                throw new IllegalArgumentException("nombre es requerido");
            Object apellidoPaterno = persona.get("apellido_paterno");
            if (apellidoPaterno == null || apellidoPaterno.toString().isBlank())
                throw new IllegalArgumentException("apellido_paterno es requerido");
            Object tipoRol = laborales.get("tipo_rol");
            if (tipoRol == null || tipoRol.toString().isBlank())
                throw new IllegalArgumentException("tipo_rol es requerido");
        }
    }

    UUID registrar(Command cmd);
}
