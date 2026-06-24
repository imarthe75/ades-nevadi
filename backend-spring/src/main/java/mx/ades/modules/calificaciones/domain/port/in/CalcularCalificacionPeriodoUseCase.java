package mx.ades.modules.calificaciones.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para calcular la calificación de período
 * de un alumno en una materia invocando la función PostgreSQL
 * {@code calcular_calificacion_periodo} en el dominio de calificaciones.
 *
 * @author ADES
 * @since 2026
 */
public interface CalcularCalificacionPeriodoUseCase {
    void ejecutar(UUID estudianteId, UUID inscripcionId, UUID materiaId, UUID periodoId);
}
