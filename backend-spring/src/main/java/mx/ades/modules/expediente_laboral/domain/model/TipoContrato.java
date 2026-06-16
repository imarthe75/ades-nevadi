package mx.ades.modules.expediente_laboral.domain.model;

import java.util.Arrays;

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
