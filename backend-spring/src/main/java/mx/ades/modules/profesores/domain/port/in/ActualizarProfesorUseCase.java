package mx.ades.modules.profesores.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface ActualizarProfesorUseCase {

    record Command(
            UUID profesorId,
            Map<String, Object> persona,
            Map<String, Object> laborales
    ) {
        public Command {
            if (profesorId == null)
                throw new IllegalArgumentException("profesor_id es requerido");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}
