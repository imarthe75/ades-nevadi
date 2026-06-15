package mx.ades.modules.expediente.domain.port.in;

import java.util.UUID;

public interface VerificarExpedienteUseCase {

    record Command(UUID expedienteId, String observaciones, int nivelAcceso, UUID verificadoPorId) {
        public Command {
            if (nivelAcceso > 2)
                throw new IllegalArgumentException("Solo el Director/Admin puede verificar expedientes");
        }
    }

    void ejecutar(Command command);
}
