package mx.ades.modules.conducta.domain.model;

/**
 * Estado del plan de mejora conductual de un alumno.
 * <p>Valores: BORRADOR (recién creado), EN_PROCESO (en seguimiento activo),
 * COMPLETADO (objetivos alcanzados), ABANDONADO (plan interrumpido).
 * Solo BORRADOR y EN_PROCESO permiten añadir nuevos seguimientos.</p>
 *
 * @author ADES
 * @since 2026
 */
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
