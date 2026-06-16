package mx.ades.modules.learning_paths.domain.port.in;

import java.util.Map;
import java.util.UUID;

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
