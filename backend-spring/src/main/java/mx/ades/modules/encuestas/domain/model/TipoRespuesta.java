package mx.ades.modules.encuestas.domain.model;

public enum TipoRespuesta {
    ESCALA_5,
    OPCION_MULTIPLE,
    BOOLEANO,
    TEXTO_LIBRE;

    public boolean esEscala() { return this == ESCALA_5; }
    public boolean esTextoLibre() { return this == TEXTO_LIBRE; }
    public boolean requiereValorNumerico() { return this == ESCALA_5; }

    public static TipoRespuesta of(String valor) {
        if (valor == null) return TEXTO_LIBRE;
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TEXTO_LIBRE;
        }
    }
}
