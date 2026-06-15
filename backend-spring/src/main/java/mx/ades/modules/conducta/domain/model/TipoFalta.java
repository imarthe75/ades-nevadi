package mx.ades.modules.conducta.domain.model;

public enum TipoFalta {
    LEVE,
    GRAVE,
    MUY_GRAVE;

    public boolean requiereSeguimiento() {
        return this == GRAVE || this == MUY_GRAVE;
    }
}
