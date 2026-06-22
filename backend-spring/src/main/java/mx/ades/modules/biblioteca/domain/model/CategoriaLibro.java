package mx.ades.modules.biblioteca.domain.model;

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
