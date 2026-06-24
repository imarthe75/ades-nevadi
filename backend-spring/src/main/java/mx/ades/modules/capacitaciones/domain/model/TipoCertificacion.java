package mx.ades.modules.capacitaciones.domain.model;

/**
 * Tipo de dominio que representa la clase de certificación obtenida en una capacitación docente.
 *
 * <p>Valores posibles: {@code CURSO}, {@code TALLER}, {@code DIPLOMADO},
 * {@code POSGRADO}, {@code CERTIFICACION}, {@code CONGRESO}, {@code OTRO}.
 * Los valores {@code DIPLOMADO}, {@code POSGRADO} y {@code CERTIFICACION}
 * se consideran formación formal.</p>
 *
 * @author ADES
 * @since 2026
 */
public enum TipoCertificacion {
    CURSO, TALLER, DIPLOMADO, POSGRADO, CERTIFICACION, CONGRESO, OTRO;

    public boolean esFormacionFormal() {
        return this == DIPLOMADO || this == POSGRADO || this == CERTIFICACION;
    }

    public static TipoCertificacion of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("tipo_certificacion es requerido");
        try {
            return TipoCertificacion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "tipo_certificacion inválido: " + value +
                    ". Válidos: CURSO, TALLER, DIPLOMADO, POSGRADO, CERTIFICACION, CONGRESO, OTRO");
        }
    }
}
