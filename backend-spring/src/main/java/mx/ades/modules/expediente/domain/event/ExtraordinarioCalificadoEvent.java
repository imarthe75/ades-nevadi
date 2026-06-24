package mx.ades.modules.expediente.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio publicado cuando se califica un examen extraordinario UAEMEX.
 * <p>Incluye si el alumno acreditó el examen, para efectos de actualización de kardex.</p>
 *
 * @author ADES
 * @since 2026
 */
public record ExtraordinarioCalificadoEvent(
        UUID extraordinarioId,
        UUID estudianteId,
        BigDecimal calificacion,
        boolean acredito,
        String aplicadoPor,
        Instant ocurridoEn) {}
