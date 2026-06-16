package mx.ades.modules.capacitaciones.domain.model;

public enum ModalidadCapacitacion {
    PRESENCIAL, EN_LINEA, HIBRIDA;

    public boolean tienePresenciaFisica() {
        return this == PRESENCIAL || this == HIBRIDA;
    }

    public static ModalidadCapacitacion of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("modalidad es requerida");
        try {
            return ModalidadCapacitacion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "modalidad inválida: " + value +
                    ". Válidas: PRESENCIAL, EN_LINEA, HIBRIDA");
        }
    }
}
