package mx.ades.modules.asistencias.domain.port.in;

import mx.ades.modules.asistencias.domain.model.EstatusAsistencia;

import java.util.UUID;

public record RegistrarAsistenciaCommand(
        UUID claseId,
        UUID estudianteId,
        EstatusAsistencia estatus,
        String observacion
) {}
