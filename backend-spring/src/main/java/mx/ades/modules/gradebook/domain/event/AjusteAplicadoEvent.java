package mx.ades.modules.gradebook.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AjusteAplicadoEvent(
        UUID calPeriodoId,
        BigDecimal ajuste,
        BigDecimal calificacionFinal,
        String aplicadoPor,
        Instant ocurridoEn) {}
