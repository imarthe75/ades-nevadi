package mx.ades.modules.biblioteca.domain.model;

/**
 * Tipo de dominio que representa la categoría temática de un libro en la biblioteca.
 *
 * <p>Valores posibles: {@code LITERATURA}, {@code CIENCIA}, {@code HISTORIA},
 * {@code MATEMATICAS}, {@code ARTE}, {@code TECNOLOGIA}, {@code INFANTIL},
 * {@code CONSULTA}, {@code TEXTO}, {@code OTRO}.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum CategoriaLibro {
    LITERATURA, CIENCIA, HISTORIA, MATEMATICAS, ARTE,
    TECNOLOGIA, INFANTIL, CONSULTA, TEXTO, OTRO;

    public static CategoriaLibro of(String value) {
        if (value == null || value.isBlank()) return OTRO;
        try {
            return CategoriaLibro.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "categoria inválida: " + value +
                    ". Válidas: LITERATURA, CIENCIA, HISTORIA, MATEMATICAS, ARTE, " +
                    "TECNOLOGIA, INFANTIL, CONSULTA, TEXTO, OTRO");
        }
    }
}
