package mx.ades.modules.evaluaciones.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para calificaciones de evaluaciones
 * en {@code ades_calificaciones_evaluaciones}.
 * <p>Soporta el patrón upsert: busca id activo existente para actualizar o inserta si no hay.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CalificacionEvaluacionRepositoryPort {

    Optional<UUID> findIdActiva(UUID evaluacionId, UUID estudianteId);

    void insertar(UUID evaluacionId, UUID estudianteId, double calificacion,
                  String comentarios, String username);

    void actualizar(UUID calificacionId, double calificacion, String comentarios, String username);
}
