package mx.ades.modules.learning_paths.domain.model;

/**
 * Estado de progreso de una asignación de learning path: PENDIENTE, EN_PROGRESO o COMPLETADO.
 * La transición se calcula automáticamente en función de los recursos obligatorios completados.
 *
 * @author ADES
 * @since 2026
 */
public enum EstatusAsignacion {
    PENDIENTE, EN_PROGRESO, COMPLETADO;

    public boolean esFinal() { return this == COMPLETADO; }

    public EstatusAsignacion transicion(int recursosCompletados, int totalObligatorios) {
        if (recursosCompletados >= totalObligatorios && totalObligatorios > 0) return COMPLETADO;
        return recursosCompletados > 0 ? EN_PROGRESO : PENDIENTE;
    }

    public static EstatusAsignacion of(String valor) {
        if (valor == null) return PENDIENTE;
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDIENTE;
        }
    }
}
