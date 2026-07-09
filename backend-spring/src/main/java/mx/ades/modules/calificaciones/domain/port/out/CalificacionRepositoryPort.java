package mx.ades.modules.calificaciones.domain.port.out;

import mx.ades.modules.calificaciones.domain.model.Calificacion;

import java.util.List;
import java.util.UUID;

public interface CalificacionRepositoryPort {
    /** Invoca calcular_calificacion_periodo() en PostgreSQL y devuelve el resultado actualizado. */
    Calificacion calcular(UUID inscripcionId, UUID materiaId, UUID periodoId);

    Calificacion guardar(Calificacion calificacion);

    /**
     * Obtener calificaciones de un estudiante con scoping de seguridad.
     * @param estudianteId ID del estudiante
     * @param usuarioActual ID del usuario solicitante (para scoping de caché y BOLA prevention)
     * @return Lista de calificaciones
     */
    List<Calificacion> findByEstudianteId(UUID estudianteId, UUID usuarioActual);
}
