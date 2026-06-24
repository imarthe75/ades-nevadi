package mx.ades.modules.expediente.domain.port.in;

import mx.ades.modules.expediente.domain.model.CalificacionExtra;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para calificar un examen extraordinario UAEMEX
 * en el módulo expediente.
 * <p>Publica {@code ExtraordinarioCalificadoEvent} al finalizar para actualizar el kardex.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CalificarExtraordinarioUseCase {

    record Command(
            UUID extraordinarioId,
            CalificacionExtra calificacion,
            boolean acredita,
            LocalDate fechaExamen,
            String username) {}

    void ejecutar(Command command);
}
