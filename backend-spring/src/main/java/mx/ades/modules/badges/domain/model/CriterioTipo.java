package mx.ades.modules.badges.domain.model;

import java.util.Arrays;

/**
 * Tipo de dominio que representa el criterio de otorgamiento de un badge.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code MANUAL} — el badge lo otorga manualmente un docente o coordinador.</li>
 *   <li>{@code AUTOMATICO} — el sistema evalúa la métrica configurada y lo otorga automáticamente.</li>
 * </ul>
 * </p>
 *
 * @author ADES
 * @since 2026
 */
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
