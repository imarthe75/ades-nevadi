package mx.ades.modules.gradebook.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Value Object que representa el estado actual de una calificación de periodo:
 * su valor calculado y si ya fue cerrada para edición.
 *
 * @author ADES
 * @since 2026
 */
public record CalificacionEstado(UUID id, BigDecimal calificacionCalculada, boolean cerrada) {

    public boolean permiteAjuste(boolean esAdmin) {
        return !cerrada || esAdmin;
    }
}
