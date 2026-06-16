package mx.ades.modules.justificaciones.domain.model;

public enum TipoJustificacion {
    MEDICA, FAMILIAR, DEPORTIVA, CULTURAL, ADMINISTRATIVA, OTRA;

    public static TipoJustificacion of(String value) {
        if (value == null) return MEDICA;
        try {
            return TipoJustificacion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo_justificacion inválido: " + value +
                    ". Válidos: MEDICA, FAMILIAR, DEPORTIVA, CULTURAL, ADMINISTRATIVA, OTRA");
        }
    }
}
