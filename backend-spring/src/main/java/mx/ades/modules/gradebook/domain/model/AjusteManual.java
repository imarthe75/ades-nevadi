package mx.ades.modules.gradebook.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value Object que representa un ajuste manual aplicado a una calificación calculada.
 * Requiere una justificación de al menos 20 caracteres para garantizar trazabilidad.
 *
 * @author ADES
 * @since 2026
 */
public record AjusteManual(BigDecimal valor, String justificacion) {

    private static final int MIN_JUSTIFICACION = 20;

    public AjusteManual {
        if (valor == null) throw new NullPointerException("ajuste requerido");
        if (justificacion == null || justificacion.trim().length() < MIN_JUSTIFICACION)
            throw new IllegalArgumentException(
                "La justificación debe tener al menos " + MIN_JUSTIFICACION + " caracteres");
        justificacion = justificacion.trim();
    }

    public BigDecimal calcularFinal(BigDecimal calificacionCalculada) {
        BigDecimal base = calificacionCalculada != null ? calificacionCalculada : BigDecimal.ZERO;
        return base.add(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
