package mx.ades.modules.expediente.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para marcar un expediente escolar como verificado
 * en el módulo expediente.
 * <p>Solo usuarios con nivel de acceso 2 o inferior (Admin o Director) pueden verificar.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface VerificarExpedienteUseCase {

    record Command(UUID expedienteId, String observaciones, int nivelAcceso, UUID verificadoPorId) {
        public Command {
            if (nivelAcceso > 2)
                throw new IllegalArgumentException("Solo el Director/Admin puede verificar expedientes");
        }
    }

    void ejecutar(Command command);
}
