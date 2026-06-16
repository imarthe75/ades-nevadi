package mx.ades.modules.movilidad.domain.model;

public enum TipoMovilidad {

    CAMBIO_GRUPO,
    TRASLADO,
    BAJA_TEMPORAL,
    BAJA_DEFINITIVA,
    REACTIVACION;

    /** Nivel máximo de acceso permitido (1=superadmin, 2=director, 3=coordinador). */
    public int nivelAccesoMinimo() {
        return switch (this) {
            case TRASLADO, BAJA_DEFINITIVA -> 2;  // solo director o superior
            case CAMBIO_GRUPO, BAJA_TEMPORAL, REACTIVACION -> 3;  // coordinador o superior
        };
    }

    /** El tipo de movimiento desactiva el registro de estudiante. */
    public boolean desactivaEstudiante() {
        return this == BAJA_TEMPORAL || this == BAJA_DEFINITIVA;
    }

    /** El tipo deja la inscripción activa (cambio de grupo mantiene inscripción). */
    public boolean mantienePeriodo() {
        return this == CAMBIO_GRUPO;
    }

    /** El movimiento requiere registrar una Baja en ades_bajas. */
    public boolean generaRegistroBaja() {
        return this == TRASLADO || this == BAJA_TEMPORAL || this == BAJA_DEFINITIVA;
    }

    /** Nombre del tipo_baja para INSERT en ades_bajas. */
    public String tipoBajaDb() {
        return switch (this) {
            case TRASLADO      -> "TRASLADO";
            case BAJA_TEMPORAL  -> "TEMPORAL";
            case BAJA_DEFINITIVA -> "DEFINITIVA";
            default -> throw new UnsupportedOperationException("No aplica para " + name());
        };
    }

    /** El movimiento requiere que el estudiante tenga una inscripción activa. */
    public boolean requiereInscripcionActiva() {
        return this != REACTIVACION;
    }

    public boolean permitePara(int nivelAccesoUsuario) {
        return nivelAccesoUsuario <= nivelAccesoMinimo();
    }

    public static TipoMovilidad of(String valor) {
        if (valor == null) return CAMBIO_GRUPO;
        try { return valueOf(valor.toUpperCase()); }
        catch (IllegalArgumentException e) { return CAMBIO_GRUPO; }
    }
}
