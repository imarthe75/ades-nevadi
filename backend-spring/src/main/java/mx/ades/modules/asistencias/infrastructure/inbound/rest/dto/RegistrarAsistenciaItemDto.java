package mx.ades.modules.asistencias.infrastructure.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import mx.ades.modules.asistencias.domain.model.EstatusAsistencia;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaCommand;

import java.util.UUID;

public record RegistrarAsistenciaItemDto(
        @JsonProperty("clase_id")           UUID claseId,
        @JsonProperty("estudiante_id")      UUID estudianteId,
        @JsonProperty("estatus_asistencia") String estatusAsistencia,
        String observacion
) {
    public RegistrarAsistenciaCommand toCommand() {
        EstatusAsistencia estatus = (estatusAsistencia != null)
                ? EstatusAsistencia.valueOf(estatusAsistencia.toUpperCase())
                : EstatusAsistencia.AUSENTE;
        return new RegistrarAsistenciaCommand(claseId, estudianteId, estatus, observacion);
    }
}
