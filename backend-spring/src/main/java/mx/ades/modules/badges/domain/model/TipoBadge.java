package mx.ades.modules.badges.domain.model;

import java.util.Arrays;

/**
 * Tipo de dominio que representa la categoría de un badge estudiantil.
 *
 * <p>Valores posibles: {@code ACADEMICO}, {@code CONDUCTA}, {@code ASISTENCIA},
 * {@code PARTICIPACION}, {@code LIDERAZGO}, {@code ESPECIAL}.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum TipoBadge {
    ACADEMICO, CONDUCTA, ASISTENCIA, PARTICIPACION, LIDERAZGO, ESPECIAL;

    public static TipoBadge of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_badge es requerido");
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tipo_badge inválido: " + value));
    }

    public static TipoBadge ofNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return of(value);
    }
}
