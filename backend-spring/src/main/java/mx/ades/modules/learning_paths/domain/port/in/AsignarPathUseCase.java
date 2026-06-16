package mx.ades.modules.learning_paths.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface AsignarPathUseCase {

    Map<String, Object> asignar(Command cmd);

    record Command(
        UUID pathId,
        UUID estudianteId,
        UUID asignadoPor,
        String motivo
    ) {
        public Command {
            if (pathId == null) throw new IllegalArgumentException("pathId es requerido");
            if (estudianteId == null) throw new IllegalArgumentException("estudianteId es requerido");
        }
    }
}
