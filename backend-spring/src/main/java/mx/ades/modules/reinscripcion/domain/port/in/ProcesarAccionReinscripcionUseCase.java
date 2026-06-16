package mx.ades.modules.reinscripcion.domain.port.in;

import mx.ades.modules.reinscripcion.domain.model.AccionReinscripcion;

import java.util.UUID;

public interface ProcesarAccionReinscripcionUseCase {

    record Command(UUID registroId, AccionReinscripcion accion, String razonRechazo, UUID procesadoPor) {
        public Command {
            if (accion.requiereRazon() && (razonRechazo == null || razonRechazo.isBlank())) {
                throw new IllegalArgumentException("razon_rechazo es obligatoria al rechazar");
            }
        }
    }

    record Result(UUID registroId, String estado) {}

    Result ejecutar(Command command);
}
