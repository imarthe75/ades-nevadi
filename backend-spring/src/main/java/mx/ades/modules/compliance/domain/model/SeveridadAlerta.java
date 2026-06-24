package mx.ades.modules.compliance.domain.model;

import java.util.Arrays;

/**
 * Clasificación de urgencia de una alerta de cumplimiento.
 * <p>Valores: BAJA, MEDIA (default), ALTA, CRITICA.
 * Los niveles ALTA y CRITICA se consideran urgentes y disparan notificaciones inmediatas.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum SeveridadAlerta {
    BAJA, MEDIA, ALTA, CRITICA;

    public boolean esUrgente() {
        return this == ALTA || this == CRITICA;
    }

    public static SeveridadAlerta of(String value) {
        if (value == null || value.isBlank()) return MEDIA;
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("severidad_alerta inválida: " + value));
    }
}
