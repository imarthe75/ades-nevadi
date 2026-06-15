package mx.ades.modules.evaluaciones.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemCalificacion(UUID estudianteId, BigDecimal calificacion, String comentario) {

    public ItemCalificacion {
        if (estudianteId == null) throw new NullPointerException("estudianteId requerido");
        if (calificacion == null) throw new NullPointerException("calificacion requerida");
        if (calificacion.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La calificación no puede ser negativa");
        }
    }

    public boolean excedePuntajeMaximo(BigDecimal puntajeMaximo) {
        return puntajeMaximo != null && calificacion.compareTo(puntajeMaximo) > 0;
    }
}
