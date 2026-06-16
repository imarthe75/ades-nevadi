package mx.ades.modules.compliance.domain.model;

import java.util.Arrays;

public enum EstadoAlerta {
    PENDIENTE, EN_PROCESO, RESUELTA, CANCELADA;

    public boolean esFinal() {
        return this == RESUELTA || this == CANCELADA;
    }

    public boolean permiteAccion() {
        return !esFinal();
    }

    public static EstadoAlerta of(String value) {
        if (value == null || value.isBlank()) return PENDIENTE;
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("estado_alerta inválido: " + value));
    }
}
