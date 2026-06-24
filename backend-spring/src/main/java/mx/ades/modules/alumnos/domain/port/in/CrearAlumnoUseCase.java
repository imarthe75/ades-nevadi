package mx.ades.modules.alumnos.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para crear un nuevo alumno en el dominio
 * de alumnos, incluyendo la validación de CURP (18 caracteres) y generación de matrícula.
 *
 * @author ADES
 * @since 2026
 */
public interface CrearAlumnoUseCase {

    record Command(
            String nombre,
            String apellidoPaterno,
            String apellidoMaterno,
            String curp,
            UUID plantelId,
            String usuarioCreacion
    ) {
        public Command {
            if (nombre == null || nombre.isBlank())
                throw new IllegalArgumentException("El nombre es requerido");
            if (apellidoPaterno == null || apellidoPaterno.isBlank())
                throw new IllegalArgumentException("El apellido paterno es requerido");
            if (curp == null || curp.isBlank())
                throw new IllegalArgumentException("La CURP es requerida");
            if (curp.length() != 18)
                throw new IllegalArgumentException("La CURP debe tener exactamente 18 caracteres");
        }
    }

    Map<String, Object> crear(Command cmd);
}
