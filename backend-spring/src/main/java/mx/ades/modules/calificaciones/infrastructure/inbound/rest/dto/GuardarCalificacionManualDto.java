package mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
        @JsonProperty("estudiante_id")      @NotNull(message = "estudiante_id es obligatorio") UUID estudianteId,
        @JsonProperty("grupo_id")           @NotNull(message = "grupo_id es obligatorio") UUID grupoId,
        @JsonProperty("materia_id")         @NotNull(message = "materia_id es obligatorio") UUID materiaId,
        @JsonProperty("periodo_id")         @NotNull(message = "periodo_id es obligatorio") UUID periodoId,
        @JsonProperty("calificacion_final")
        @NotNull(message = "calificacion_final es obligatorio")
        @DecimalMin(value = "0", message = "calificacion_final mínimo 0")
        @DecimalMax(value = "10", message = "calificacion_final máximo 10")
        BigDecimal calificacionFinal,
        @Size(max = 500, message = "observaciones máximo 500 caracteres")
        String observaciones
) {
    public Calificacion toDomain() {
        return new Calificacion(null, estudianteId, grupoId, materiaId,
                periodoId, calificacionFinal, false, observaciones);
    }
}
