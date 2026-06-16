package mx.ades.modules.licencias.domain.model;

public enum EstadoLicencia {
    PENDIENTE, APROBADA, RECHAZADA, CANCELADA;

    /** Only PENDIENTE licenses may be edited, approved, rejected, or cancelled */
    public boolean permiteModificacion() { return this == PENDIENTE; }

    public boolean esResuelto() {
        return this == APROBADA || this == RECHAZADA || this == CANCELADA;
    }
}
