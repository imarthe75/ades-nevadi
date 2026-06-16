package mx.ades.modules.learning_paths.domain.port.in;

import java.util.UUID;

public interface RegistrarProgresoUseCase {

    record Command(UUID asignacionId, UUID recursoId, Integer tiempoMin, Double calificacion) {
        public Command {
            if (asignacionId == null) throw new IllegalArgumentException("asignacion_id es obligatorio");
            if (recursoId == null) throw new IllegalArgumentException("recurso_id es obligatorio");
        }
    }

    record Result(String estatus, double pctCompletado) {}

    Result ejecutar(Command command);
}
