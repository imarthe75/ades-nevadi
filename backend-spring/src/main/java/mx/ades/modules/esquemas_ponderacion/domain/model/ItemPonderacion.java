package mx.ades.modules.esquemas_ponderacion.domain.model;

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
