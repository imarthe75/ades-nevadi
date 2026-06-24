package mx.ades.modules.calificaciones.infrastructure.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * DTO de entrada REST para solicitar el cálculo automático de calificación de período.
 *
 * @author ADES
 * @since 2026
 */
public record CalcularCalificacionDto(
        @JsonProperty("estudiante_id")  UUID estudianteId,
        @JsonProperty("inscripcion_id") UUID inscripcionId,
        @JsonProperty("materia_id")     UUID materiaId,
        @JsonProperty("periodo_id")     UUID periodoId
) {}
