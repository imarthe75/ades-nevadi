package mx.ades.modules.evaluaciones.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Valor de objeto que encapsula la calificación de un estudiante para una actividad evaluable.
 * <p>La calificación no puede ser negativa. Se verifica opcionalmente contra el puntaje máximo
 * configurado en la tarea mediante {@link #excedePuntajeMaximo}.</p>
 *
 * @author ADES
 * @since 2026
 */
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
