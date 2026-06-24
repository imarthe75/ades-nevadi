package mx.ades.modules.gradebook.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio publicado cuando se aplica un ajuste manual a una calificación de periodo.
 *
 * @author ADES
 * @since 2026
 */
public record AjusteAplicadoEvent(
        UUID calPeriodoId,
        BigDecimal ajuste,
        BigDecimal calificacionFinal,
        String aplicadoPor,
        Instant ocurridoEn) {}
