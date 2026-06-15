package mx.ades.modules.evaluaciones.domain.model;

public enum EstatusEntrega {
    PENDIENTE, ENTREGADA, CALIFICADA, EXCUSA;

    public boolean puedeCalificarse() {
        return this == ENTREGADA || this == PENDIENTE;
    }

    public boolean estaCalificada() {
        return this == CALIFICADA;
    }
}
