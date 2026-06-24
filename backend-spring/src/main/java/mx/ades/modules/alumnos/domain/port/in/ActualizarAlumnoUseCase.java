package mx.ades.modules.alumnos.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para actualizar los datos de un alumno
 * (persona y complementarios) en el dominio de alumnos.
 *
 * @author ADES
 * @since 2026
 */
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
