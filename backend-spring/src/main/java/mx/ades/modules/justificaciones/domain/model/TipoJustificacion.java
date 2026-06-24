package mx.ades.modules.justificaciones.domain.model;

/**
 * Tipo de justificación de falta: MEDICA, FAMILIAR, DEPORTIVA, CULTURAL, ADMINISTRATIVA u OTRA.
 *
 * @author ADES
 * @since 2026
 */
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
