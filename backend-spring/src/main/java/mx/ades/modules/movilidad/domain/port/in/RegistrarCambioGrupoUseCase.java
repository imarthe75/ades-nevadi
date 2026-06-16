package mx.ades.modules.movilidad.domain.port.in;

import java.util.UUID;

public interface RegistrarCambioGrupoUseCase {

    record Command(
            UUID estudianteId,
            UUID grupoDestinoId,
            String motivo,
            UUID cicloEscolarId,
            UUID autorizadoPorId,
            String usuarioModificacion
    ) {
        public Command {
            if (estudianteId == null) throw new IllegalArgumentException("estudianteId requerido");
            if (grupoDestinoId == null) throw new IllegalArgumentException("grupoDestinoId requerido");
            if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("motivo requerido");
        }
    }

    record Result(String grupoAnterior, String grupoNuevo, UUID cambioId) {}

    Result ejecutar(Command command);
}
