package mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import mx.ades.modules.calificaciones.domain.model.Calificacion;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de entrada REST para guardar manualmente una calificación de período.
 *
 * @author ADES
 * @since 2026
 */
public record GuardarCalificacionManualDto(
        @JsonProperty("estudiante_id")      UUID estudianteId,
        @JsonProperty("grupo_id")           UUID grupoId,
        @JsonProperty("materia_id")         UUID materiaId,
        @JsonProperty("periodo_id")         UUID periodoId,
        @JsonProperty("calificacion_final") BigDecimal calificacionFinal,
        String observaciones
) {
    public Calificacion toDomain() {
        return new Calificacion(null, estudianteId, grupoId, materiaId,
                periodoId, calificacionFinal, false, observaciones);
    }
}
