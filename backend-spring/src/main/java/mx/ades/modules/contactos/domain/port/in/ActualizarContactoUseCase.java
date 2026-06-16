package mx.ades.modules.contactos.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface ActualizarContactoUseCase {

    Map<String, Object> actualizar(Command cmd);

    record Command(
        UUID contactoId,
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
        Integer rowVersion,
        String usuarioModificacion
    ) {
        public Command {
            if (contactoId == null) throw new IllegalArgumentException("contactoId es requerido");
        }
    }
}
