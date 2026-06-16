package mx.ades.modules.conducta.domain.model;

public enum EstadoPlan {
    BORRADOR, EN_PROCESO, COMPLETADO, ABANDONADO;

    public boolean permiteNuevoSeguimiento() {
        return this == BORRADOR || this == EN_PROCESO;
    }

    public boolean esCerrado() {
        return this == COMPLETADO || this == ABANDONADO;
    }

    public static EstadoPlan of(String valor) {
        if (valor == null) return BORRADOR;
        try {
            return EstadoPlan.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("estado_plan inválido: " + valor);
        }
    }
}
