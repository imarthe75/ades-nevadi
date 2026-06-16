package mx.ades.modules.entregas.domain.model;

import java.util.Arrays;

public enum EstatusEntrega {
    PENDIENTE, ENTREGADA, CALIFICADA, EXCUSA;

    public boolean esCalificable() {
        return this == ENTREGADA;
    }

    public boolean esTerminal() {
        return this == CALIFICADA || this == EXCUSA;
    }

    public static EstatusEntrega of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("estatus_entrega es requerido");
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("estatus_entrega inválido: " + value));
    }
}
