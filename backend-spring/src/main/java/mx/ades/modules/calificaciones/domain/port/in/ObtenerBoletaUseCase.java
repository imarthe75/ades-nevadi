package mx.ades.modules.calificaciones.domain.port.in;

import mx.ades.modules.calificaciones.domain.model.Calificacion;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para obtener todas las calificaciones de período
 * de un alumno (boleta de notas) en el dominio de calificaciones.
 *
 * @author ADES
 * @since 2026
 */
public interface ObtenerBoletaUseCase {
    List<Calificacion> ejecutar(UUID estudianteId);

    /**
     * Obtener boleta con scoping de usuario para BOLA prevention.
     * @param estudianteId ID del estudiante
     * @param usuarioId ID del usuario solicitante (usado para cache scoping)
     * @return Lista de calificaciones del estudiante
     */
    default List<Calificacion> ejecutar(UUID estudianteId, UUID usuarioId) {
        return ejecutar(estudianteId);
    }
}
