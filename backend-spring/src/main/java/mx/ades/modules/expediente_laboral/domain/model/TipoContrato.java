package mx.ades.modules.expediente_laboral.domain.model;

import java.util.Arrays;

/**
 * Tipo de contrato laboral del personal: INDEFINIDO, DETERMINADO, HONORARIOS o COMISION.
 *
 * @author ADES
 * @since 2026
 */
public enum TipoContrato {
    INDEFINIDO, DETERMINADO, HONORARIOS, COMISION;

    public static TipoContrato of(String value) {
        if (value == null || value.isBlank())
            return INDEFINIDO;
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tipo_contrato inválido: " + value));
    }
}
