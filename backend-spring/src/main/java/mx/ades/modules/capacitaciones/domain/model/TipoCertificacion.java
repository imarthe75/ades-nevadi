package mx.ades.modules.capacitaciones.domain.model;

public enum TipoCertificacion {
    CURSO, TALLER, DIPLOMADO, POSGRADO, CERTIFICACION, CONGRESO, OTRO;

    public boolean esFormacionFormal() {
        return this == DIPLOMADO || this == POSGRADO || this == CERTIFICACION;
    }

    public static TipoCertificacion of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_certificacion es requerido");
        try {
            return TipoCertificacion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "tipo_certificacion inválido: " + value +
                    ". Válidos: CURSO, TALLER, DIPLOMADO, POSGRADO, CERTIFICACION, CONGRESO, OTRO");
        }
    }
}
