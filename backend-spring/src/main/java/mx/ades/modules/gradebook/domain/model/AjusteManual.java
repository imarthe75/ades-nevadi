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
        // IllegalArgumentException (no NullPointerException): es la excepción que
        // GlobalExceptionHandler traduce a 400 limpio; una NPE cae en el catch-all
        // de Exception y produce un 500 genérico ante un simple campo faltante.
        if (valor == null) throw new IllegalArgumentException("ajuste requerido");
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
