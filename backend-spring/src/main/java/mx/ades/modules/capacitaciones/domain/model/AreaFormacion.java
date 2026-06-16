package mx.ades.modules.capacitaciones.domain.model;

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
