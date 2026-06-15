package mx.ades.modules.calificaciones.domain.model;

public enum EstatusPromocion {
    APROBADO,
    REPROBADO;

    public static EstatusPromocion from(boolean esAcreditado) {
        return esAcreditado ? APROBADO : REPROBADO;
    }

    public boolean esReprobado() {
        return this == REPROBADO;
    }
}
