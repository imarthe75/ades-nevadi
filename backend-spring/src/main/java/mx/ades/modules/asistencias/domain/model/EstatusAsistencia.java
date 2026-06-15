package mx.ades.modules.asistencias.domain.model;

public enum EstatusAsistencia {
    PRESENTE,
    AUSENTE,
    TARDANZA,
    JUSTIFICADO;

    public boolean esAusencia() {
        return this == AUSENTE;
    }

    public boolean cuentaComoAsistencia() {
        return this == PRESENTE || this == TARDANZA || this == JUSTIFICADO;
    }
}
