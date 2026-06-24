package mx.ades.modules.badges.domain.model;

import java.util.Arrays;

/**
 * Tipo de dominio que representa la métrica de evaluación automática para un badge.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code PCT_ASISTENCIA} — porcentaje de asistencia del alumno en el ciclo.</li>
 *   <li>{@code PROMEDIO_GENERAL} — promedio de calificaciones del alumno en el ciclo.</li>
 *   <li>{@code SIN_REPORTES_CONDUCTA} — alumno sin reportes de conducta desde el inicio del ciclo.</li>
 * </ul>
 * </p>
 *
 * @author ADES
 * @since 2026
 */
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
