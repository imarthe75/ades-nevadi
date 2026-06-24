package mx.ades.modules.capacitaciones.domain.model;

/**
 * Tipo de dominio que representa la modalidad de impartición de una capacitación docente.
 *
 * <p>Valores posibles y su significado:
 * <ul>
 *   <li>{@code PRESENCIAL} — se realiza en instalaciones físicas.</li>
 *   <li>{@code EN_LINEA} — completamente remota.</li>
 *   <li>{@code HIBRIDA} — combina presencia física y remota.</li>
 * </ul>
 * </p>
 *
 * @author ADES
 * @since 2026
 */
public enum ModalidadCapacitacion {
    PRESENCIAL, EN_LINEA, HIBRIDA;

    public boolean tienePresenciaFisica() {
        return this == PRESENCIAL || this == HIBRIDA;
    }

    public static ModalidadCapacitacion of(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("modalidad es requerida");
        try {
            return ModalidadCapacitacion.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "modalidad inválida: " + value +
                    ". Válidas: PRESENCIAL, EN_LINEA, HIBRIDA");
        }
    }
}
