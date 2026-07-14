package mx.ades.modules.conducta.domain.model;

/**
 * Valoración del avance registrado en un seguimiento de plan de mejora conductual.
 * <p>Valores: SIN_AVANCE, PARCIAL (valor por defecto en BD), SATISFACTORIO, EXCELENTE.
 * Coincide exactamente con el CHECK de {@code ades_seguimiento_plan.avance}
 * (ver migración {@code 034_sanciones_planes_mejora.sql}).</p>
 *
 * @author ADES
 * @since 2026
 */
public enum AvanceSeguimiento {
    SIN_AVANCE, PARCIAL, SATISFACTORIO, EXCELENTE;

    public static AvanceSeguimiento of(String valor) {
        if (valor == null || valor.isBlank())
            throw new IllegalArgumentException("avance es requerido");
        try {
            return AvanceSeguimiento.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("avance inválido: " + valor +
                    ". Válidos: SIN_AVANCE, PARCIAL, SATISFACTORIO, EXCELENTE");
        }
    }
}
