package mx.ades.modules.alumnos.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface ActualizarAlumnoUseCase {

    record Command(
            UUID alumnoId,
            Map<String, Object> persona,
            Map<String, Object> complementarios
    ) {
        public Command {
            if (alumnoId == null)
                throw new IllegalArgumentException("alumno_id es requerido");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}
