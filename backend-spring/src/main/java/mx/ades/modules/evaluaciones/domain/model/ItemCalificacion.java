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
        // IllegalArgumentException (no NullPointerException): el GlobalExceptionHandler solo
        // mapea IllegalArgumentException a 400 limpio; una NPE aquí caería en el catch-all
        // de Exception y el usuario recibiría un 500 genérico ante un simple campo faltante.
        if (estudianteId == null) throw new IllegalArgumentException("estudianteId requerido");
        if (calificacion == null) throw new IllegalArgumentException("calificacion requerida");
        if (calificacion.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La calificación no puede ser negativa");
        }
    }

    public boolean excedePuntajeMaximo(BigDecimal puntajeMaximo) {
        return puntajeMaximo != null && calificacion.compareTo(puntajeMaximo) > 0;
    }
}
