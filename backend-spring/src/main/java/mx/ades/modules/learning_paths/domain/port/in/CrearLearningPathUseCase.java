package mx.ades.modules.learning_paths.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear un learning path con criterio y umbral de activación.
 *
 * @author ADES
 * @since 2026
 */
public interface CrearLearningPathUseCase {

    Map<String, Object> crear(Command cmd);

    record Command(
        String nombre,
        String descripcion,
        UUID nivelEducativoId,
        UUID materiaId,
        String criterioActivacion,
        Double umbralActivacion
    ) {
        public Command {
            if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("nombre es requerido");
        }
    }
}
