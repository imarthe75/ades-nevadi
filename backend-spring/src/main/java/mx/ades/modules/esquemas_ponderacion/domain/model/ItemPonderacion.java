package mx.ades.modules.esquemas_ponderacion.domain.model;

import java.util.Set;

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

    /** Valores permitidos — deben coincidir EXACTAMENTE con el CHECK chk_tipo_item de ades_items_ponderacion. */
    private static final Set<String> TIPOS_VALIDOS = Set.of(
            "examen", "tarea", "proyecto", "asistencia", "comportamiento", "participacion", "laboratorio", "otro");

    public ItemPonderacion {
        if (tipoItem == null || tipoItem.isBlank())
            throw new IllegalArgumentException("tipo_item es requerido");
        if (!TIPOS_VALIDOS.contains(tipoItem))
            throw new IllegalArgumentException("tipo_item inválido: " + tipoItem + ". Valores permitidos: " + TIPOS_VALIDOS);
        if (pesoPorcentaje == null || pesoPorcentaje <= 0 || pesoPorcentaje > 100)
            throw new IllegalArgumentException("peso_porcentaje debe estar entre 0 (exclusivo) y 100");
        if (ordenDisplay == null) ordenDisplay = 1;
    }
}
