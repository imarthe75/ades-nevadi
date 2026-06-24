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
}
