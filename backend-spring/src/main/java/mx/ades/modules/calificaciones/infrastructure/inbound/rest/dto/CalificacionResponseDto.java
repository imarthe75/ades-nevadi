package mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import mx.ades.modules.calificaciones.domain.model.Calificacion;

import java.math.BigDecimal;
import java.util.UUID;

public record CalificacionResponseDto(
        UUID id,
        @JsonProperty("estudiante_id")   UUID estudianteId,
        @JsonProperty("grupo_id")        UUID grupoId,
        @JsonProperty("materia_id")      UUID materiaId,
        @JsonProperty("periodo_id")      UUID periodoId,
        @JsonProperty("calificacion_final") BigDecimal calificacionFinal,
        @JsonProperty("es_acreditado")   boolean esAcreditado,
        @JsonProperty("estatus_promocion") String estatusPromocion,
        String observaciones
) {
    public static CalificacionResponseDto from(Calificacion c) {
        return new CalificacionResponseDto(
                c.id(), c.estudianteId(), c.grupoId(), c.materiaId(), c.periodoId(),
                c.calificacionFinal(), c.esAcreditado(),
                c.estatusPromocion().name(), c.observaciones()
        );
    }
}
