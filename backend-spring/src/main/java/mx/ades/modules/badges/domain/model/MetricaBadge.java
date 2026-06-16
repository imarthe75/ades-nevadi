package mx.ades.modules.badges.domain.model;

import java.util.Arrays;

public enum MetricaBadge {
    PCT_ASISTENCIA,
    PROMEDIO_GENERAL,
    SIN_REPORTES_CONDUCTA;

    public static MetricaBadge ofNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values())
                .filter(m -> m.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("metrica_badge inválida: " + value));
    }
}
