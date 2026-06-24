package mx.ades.modules.asistencias.domain.port.in;

import mx.ades.modules.asistencias.domain.model.EstatusAsistencia;

import java.util.UUID;

/**
 * Comando de entrada para registrar la asistencia individual de un alumno a una clase.
 *
 * @author ADES
 * @since 2026
 */
public record RegistrarAsistenciaCommand(
        UUID claseId,
        UUID estudianteId,
        EstatusAsistencia estatus,
        String observacion
) {}
