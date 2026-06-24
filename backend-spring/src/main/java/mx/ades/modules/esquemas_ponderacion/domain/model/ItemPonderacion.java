package mx.ades.modules.esquemas_ponderacion.domain.model;

/**
 * Ítem de ponderación dentro de un esquema de calificación.
 * <p>Representa el tipo de actividad evaluable (p. ej. TAREA, EXAMEN), su peso porcentual
 * y el orden de presentación en la interfaz. El {@code pesoPorcentaje} debe ser positivo;
 * la suma de todos los ítems del esquema debe ser exactamente 100%.</p>
 *
 * @author ADES
 * @since 2026
 */
public record ItemPonderacion(String tipoItem, String nombrePersonalizado,
                               Double pesoPorcentaje, Integer ordenDisplay) {
    public ItemPonderacion {
        if (tipoItem == null || tipoItem.isBlank())
            throw new IllegalArgumentException("tipo_item es requerido");
        if (pesoPorcentaje == null || pesoPorcentaje <= 0)
            throw new IllegalArgumentException("peso_porcentaje debe ser positivo");
        if (ordenDisplay == null) ordenDisplay = 1;
    }
}
