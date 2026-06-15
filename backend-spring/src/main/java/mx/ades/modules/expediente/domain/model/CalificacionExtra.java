package mx.ades.modules.expediente.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calificación de examen extraordinario: rango 0-10, dos decimales. */
public record CalificacionExtra(BigDecimal valor) {

    private static final BigDecimal MIN = BigDecimal.ZERO;
    private static final BigDecimal MAX = BigDecimal.TEN;

    public CalificacionExtra {
        if (valor == null) throw new NullPointerException("calificacion requerida");
        if (valor.compareTo(MIN) < 0 || valor.compareTo(MAX) > 0)
            throw new IllegalArgumentException("Calificación debe estar entre 0 y 10, recibido: " + valor);
        valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static CalificacionExtra of(double d) {
        return new CalificacionExtra(BigDecimal.valueOf(d));
    }

    public boolean acredita(BigDecimal minimoAprobatorio) {
        return valor.compareTo(minimoAprobatorio) >= 0;
    }
}
