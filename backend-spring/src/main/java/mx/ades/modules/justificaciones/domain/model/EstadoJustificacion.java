package mx.ades.modules.justificaciones.domain.model;

public enum EstadoJustificacion {
    PENDIENTE, APROBADA, RECHAZADA;

    public boolean permiteResolucion() { return this == PENDIENTE; }

    public boolean esPendiente() { return this == PENDIENTE; }
}
