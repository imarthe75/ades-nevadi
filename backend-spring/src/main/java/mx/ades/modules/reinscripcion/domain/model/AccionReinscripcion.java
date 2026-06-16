package mx.ades.modules.reinscripcion.domain.model;

public enum AccionReinscripcion {
    APROBAR,
    RECHAZAR;

    public boolean requiereRazon() {
        return this == RECHAZAR;
    }

    public String toEstado() {
        return this == APROBAR ? "APROBADO" : "RECHAZADO";
    }

    public static AccionReinscripcion of(String valor) {
        try {
            return valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("accion inválida: " + valor +
                    ". Valores permitidos: APROBAR, RECHAZAR");
        }
    }
}
