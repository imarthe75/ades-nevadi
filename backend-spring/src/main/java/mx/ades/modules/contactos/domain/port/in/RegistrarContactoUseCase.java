package mx.ades.modules.contactos.domain.port.in;

import java.util.Map;
import java.util.UUID;

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
