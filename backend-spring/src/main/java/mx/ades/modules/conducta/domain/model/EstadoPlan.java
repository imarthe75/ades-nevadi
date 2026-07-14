package mx.ades.modules.conducta.domain.model;

/**
 * Estado del plan de mejora conductual de un alumno.
 * <p>Valores: ACTIVO (recién creado, valor por defecto en BD), EN_PROCESO (en
 * seguimiento activo), CUMPLIDO (objetivos alcanzados), INCUMPLIDO (no se
 * lograron los compromisos), CANCELADO (plan interrumpido).</p>
 * <p><strong>Nota de auditoría (2026-07):</strong> esta enum antes declaraba
 * BORRADOR/COMPLETADO/ABANDONADO — valores que nunca existieron en el
 * CHECK real de {@code ades_planes_mejora.estado} (ver migración
 * {@code 034_sanciones_planes_mejora.sql}) y que tampoco coincidían con lo que
 * envía el frontend (ACTIVO/EN_PROCESO/CUMPLIDO/INCUMPLIDO/CANCELADO). La enum
 * nunca se usaba para validar (dead code), así que el desalineamiento no había
 * causado un bug en producción — se corrige aquí y se conecta a
 * {@link mx.ades.modules.conducta.ConductaController} para validar antes de
 * escribir en BD.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum EstadoPlan {
    ACTIVO, EN_PROCESO, CUMPLIDO, INCUMPLIDO, CANCELADO;

    public boolean permiteNuevoSeguimiento() {
        return this == ACTIVO || this == EN_PROCESO;
    }

    public boolean esCerrado() {
        return this == CUMPLIDO || this == INCUMPLIDO || this == CANCELADO;
    }

    public static EstadoPlan of(String valor) {
        if (valor == null) return ACTIVO;
        try {
            return EstadoPlan.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("estado_plan inválido: " + valor +
                    ". Válidos: ACTIVO, EN_PROCESO, CUMPLIDO, INCUMPLIDO, CANCELADO");
        }
    }
}
