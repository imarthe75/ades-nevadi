package mx.ades.modules.asistencias.infrastructure.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import mx.ades.modules.asistencias.domain.model.Asistencia;

import java.util.UUID;

/**
 * DTO de salida REST para la consulta de asistencias de alumnos.
 *
 * @author ADES
 * @since 2026
 */
public record AsistenciaResponseDto(
        UUID id,
        @JsonProperty("clase_id")           UUID claseId,
        @JsonProperty("estudiante_id")      UUID estudianteId,
        @JsonProperty("estatus_asistencia") String estatusAsistencia,
        String observacion
) {
    public static AsistenciaResponseDto from(Asistencia a) {
        return new AsistenciaResponseDto(
                a.id(), a.claseId(), a.estudianteId(),
                a.estatus().name(), a.observacion()
        );
    }
}
