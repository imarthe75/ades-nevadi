package mx.ades.modules.expediente.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExtraordinarioCalificadoEvent(
        UUID extraordinarioId,
        UUID estudianteId,
        BigDecimal calificacion,
        boolean acredito,
        String aplicadoPor,
        Instant ocurridoEn) {}
