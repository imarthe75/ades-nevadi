package mx.ades.modules.procesos.domain.model;

public enum EstadoAdmision {
    PENDIENTE, ACEPTADO, RECHAZADO, LISTA_ESPERA, DIAGNOSTICO, NOTIFICADO, INSCRITO;

    public boolean permitePreinscripcion() { return this == ACEPTADO; }
    public boolean esResuelto() { return this == ACEPTADO || this == RECHAZADO || this == INSCRITO; }
    public boolean esEnEspera() { return this == LISTA_ESPERA; }

    public static EstadoAdmision of(String valor) {
        if (valor == null || valor.isBlank()) return PENDIENTE;
        try { return valueOf(valor.toUpperCase().trim()); }
        catch (IllegalArgumentException e) { return PENDIENTE; }
    }
}
