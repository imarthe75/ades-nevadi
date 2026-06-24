package mx.ades.modules.encuestas.domain.model;

/**
 * Tipo de respuesta esperada para una pregunta de encuesta.
 * <p>Valores: ESCALA_5 (numérico 1-5), OPCION_MULTIPLE (selección de lista),
 * BOOLEANO (sí/no), TEXTO_LIBRE (texto abierto, default).
 * Solo ESCALA_5 requiere valor numérico.</p>
 *
 * @author ADES
 * @since 2026
 */
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
