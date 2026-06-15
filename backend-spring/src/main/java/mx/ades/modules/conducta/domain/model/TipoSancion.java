package mx.ades.modules.conducta.domain.model;

public enum TipoSancion {
    AMONESTACION_VERBAL,
    AMONESTACION_ESCRITA,
    CITATORIO_PADRES,
    ASISTENCIA_PADRES,
    SUSPENSION,
    EXPULSION;

    /** Sanciones que obligan a notificar a padres formalmente. */
    public boolean requiereNotificacionPadres() {
        return this == SUSPENSION || this == EXPULSION || this == ASISTENCIA_PADRES;
    }
}
