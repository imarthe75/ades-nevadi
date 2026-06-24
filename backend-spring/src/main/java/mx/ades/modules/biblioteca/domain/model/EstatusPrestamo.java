package mx.ades.modules.biblioteca.domain.model;

/**
 * Tipo de dominio que representa el estado de un préstamo de libro en la biblioteca.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code PRESTADO} — el libro está actualmente prestado.</li>
 *   <li>{@code DEVUELTO} — el libro fue devuelto en tiempo.</li>
 *   <li>{@code VENCIDO} — el plazo de devolución ha expirado.</li>
 *   <li>{@code EXTRAVIADO} — el libro fue reportado como perdido.</li>
 * </ul>
 * </p>
 *
 * @author ADES
 * @since 2026
 */
public enum EstatusPrestamo {
    PRESTADO, DEVUELTO, VENCIDO, EXTRAVIADO;

    public boolean estaAbierto() {
        return this == PRESTADO || this == VENCIDO;
    }

    public static EstatusPrestamo of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("estatus es requerido");
        try {
            return EstatusPrestamo.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "estatus inválido: " + value +
                    ". Válidos: PRESTADO, DEVUELTO, VENCIDO, EXTRAVIADO");
        }
    }
}
