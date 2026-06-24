package mx.ades.modules.contactos.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para registrar un nuevo contacto familiar de alumno en el módulo contactos.
 * <p>El campo {@code nacionalidad} tiene como valor por defecto "Mexicana" si no se proporciona.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarContactoUseCase {

    Map<String, Object> registrar(Command cmd);

    record Command(
        UUID estudianteId,
        String nombreCompleto,
        String parentesco,
        String telefonoPrincipal,
        String email,
        Boolean esTutorLegal,
        Boolean esContactoEmergencia,
        Boolean puedeRecoger,
        String ocupacion,
        String nivelEstudios,
        String rfc,
        String nacionalidad,
        String usuarioCreacion
    ) {
        public Command {
            if (estudianteId == null) throw new IllegalArgumentException("estudianteId es requerido");
            if (nacionalidad == null || nacionalidad.isBlank()) nacionalidad = "Mexicana";
        }
    }
}
