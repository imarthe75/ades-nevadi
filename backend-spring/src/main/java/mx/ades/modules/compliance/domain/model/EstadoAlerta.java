package mx.ades.modules.compliance.domain.model;

import java.util.Arrays;

/**
 * Estado del ciclo de vida de una alerta de cumplimiento.
 * <p>Estados: PENDIENTE (creada, sin gestión), EN_PROCESO (en atención),
 * RESUELTA (cerrada satisfactoriamente), CANCELADA (desestimada).</p>
 *
 * @author ADES
 * @since 2026
 */
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
