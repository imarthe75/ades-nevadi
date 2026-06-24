package mx.ades.modules.capacitaciones.domain.model;

/**
 * Tipo de dominio que representa el área de formación de una capacitación docente.
 *
 * <p>Valores posibles: {@code PEDAGOGIA}, {@code TIC}, {@code DISCIPLINAR},
 * {@code IDIOMAS}, {@code LIDERAZGO}, {@code OTRO}.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum AreaFormacion {
    PEDAGOGIA, TIC, DISCIPLINAR, IDIOMAS, LIDERAZGO, OTRO;

    public static AreaFormacion ofNullable(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AreaFormacion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "area_formacion inválida: " + value +
                    ". Válidas: PEDAGOGIA, TIC, DISCIPLINAR, IDIOMAS, LIDERAZGO, OTRO");
        }
    }
}
