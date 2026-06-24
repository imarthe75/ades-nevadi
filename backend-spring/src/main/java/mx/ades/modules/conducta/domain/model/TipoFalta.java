package mx.ades.modules.conducta.domain.model;

/**
 * Clasificación de la gravedad de una falta de conducta en el módulo conducta.
 * <p>Valores: LEVE, GRAVE, MUY_GRAVE.
 * Las faltas GRAVE y MUY_GRAVE requieren seguimiento formal y notificación a padres.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum TipoFalta {
    LEVE,
    GRAVE,
    MUY_GRAVE;

    public boolean requiereSeguimiento() {
        return this == GRAVE || this == MUY_GRAVE;
    }
}
