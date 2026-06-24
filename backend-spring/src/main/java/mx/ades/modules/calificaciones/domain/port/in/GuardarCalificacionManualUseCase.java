package mx.ades.modules.calificaciones.domain.port.in;

import mx.ades.modules.calificaciones.domain.model.Calificacion;

/**
 * Puerto de entrada: define el contrato para guardar manualmente una calificación
 * de período en el dominio de calificaciones.
 *
 * <p>Usado cuando el docente captura la calificación directamente sin pasar por
 * el cálculo automático de la función PostgreSQL.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface GuardarCalificacionManualUseCase {
    Calificacion ejecutar(Calificacion calificacion);
}
