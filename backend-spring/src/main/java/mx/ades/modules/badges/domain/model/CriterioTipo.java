package mx.ades.modules.badges.domain.model;

import java.util.Arrays;

public enum CriterioTipo {
    MANUAL, AUTOMATICO;

    public boolean esAutomatico() {
        return this == AUTOMATICO;
    }

    public static CriterioTipo of(String value) {
        if (value == null || value.isBlank()) return MANUAL;
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("criterio_tipo inválido: " + value));
    }
}
