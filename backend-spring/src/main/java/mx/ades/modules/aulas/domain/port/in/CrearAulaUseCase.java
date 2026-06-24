package mx.ades.modules.aulas.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para crear un aula nueva en el dominio de aulas.
 *
 * @author ADES
 * @since 2026
 */
public interface CrearAulaUseCase {

    record Command(
            String nombreAula,
            UUID plantelId,
            String tipoAula,
            Integer capacidadAlumnos
    ) {
        public Command {
            if (nombreAula == null || nombreAula.isBlank())
                throw new IllegalArgumentException("El nombre del aula es requerido");
            if (plantelId == null)
                throw new IllegalArgumentException("El plantel es requerido");
        }
    }

    Map<String, Object> crear(Command cmd);
}
