package mx.ades.modules.evaluaciones.domain.port.in;

import java.util.List;
import java.util.UUID;

public interface CalificarEvaluacionMasivoUseCase {

    record EntradaCalificacion(UUID estudianteId, double calificacion, String comentarios) {
        public EntradaCalificacion {
            if (calificacion < 0 || calificacion > 10) {
                throw new IllegalArgumentException(
                        "Calificación fuera de rango [0,10]: " + calificacion + " para estudiante " + estudianteId);
            }
        }
    }

    record Command(UUID evaluacionId, List<EntradaCalificacion> calificaciones, String username) {
        public Command {
            if (calificaciones == null || calificaciones.isEmpty()) {
                throw new IllegalArgumentException("La lista de calificaciones no puede estar vacía");
            }
        }
    }

    int ejecutar(Command command);
}
