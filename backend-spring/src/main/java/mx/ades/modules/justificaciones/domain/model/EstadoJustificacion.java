package mx.ades.modules.justificaciones.domain.model;

/**
 * Estado del flujo de una justificación de falta: PENDIENTE, APROBADA o RECHAZADA.
 *
 * @author ADES
 * @since 2026
 */
public enum EstadoJustificacion {
    PENDIENTE, APROBADA, RECHAZADA;

    public boolean permiteResolucion() { return this == PENDIENTE; }

    public boolean esPendiente() { return this == PENDIENTE; }
}
