package mx.ades.modules.justificaciones.domain.port.in;

import mx.ades.modules.justificaciones.domain.model.TipoJustificacion;

import java.util.UUID;

public interface RegistrarJustificacionUseCase {

    record Command(
            UUID asistenciaId,
            TipoJustificacion tipo,
            String motivo,
            String documentoUrl,
            UUID usuarioId
    ) {
        public Command {
            if (asistenciaId == null)
                throw new IllegalArgumentException("asistencia_id es requerida");
            if (tipo == null)
                tipo = TipoJustificacion.MEDICA;
        }
    }

    UUID registrar(Command cmd);
}
