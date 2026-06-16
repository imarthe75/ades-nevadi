package mx.ades.modules.evaluaciones.domain.port.out;

import java.util.Optional;
import java.util.UUID;

public interface CalificacionEvaluacionRepositoryPort {

    Optional<UUID> findIdActiva(UUID evaluacionId, UUID estudianteId);

    void insertar(UUID evaluacionId, UUID estudianteId, double calificacion,
                  String comentarios, String username);

    void actualizar(UUID calificacionId, double calificacion, String comentarios, String username);
}
