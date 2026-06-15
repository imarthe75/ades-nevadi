package mx.ades.modules.expediente.domain.model;

public enum TipoBaja {
    TEMPORAL,
    DEFINITIVA,
    TRASLADO,
    DESERCION;

    /** Determina si el tipo de baja implica desactivar al estudiante en el sistema. */
    public boolean desactivaEstudiante() {
        return this == DEFINITIVA || this == DESERCION;
    }

    public static TipoBaja of(String valor) {
        try {
            return valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("tipo_baja inválido: " + valor +
                    ". Valores permitidos: TEMPORAL, DEFINITIVA, TRASLADO, DESERCION");
        }
    }
}
