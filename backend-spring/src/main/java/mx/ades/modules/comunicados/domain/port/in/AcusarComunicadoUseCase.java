package mx.ades.modules.comunicados.domain.port.in;

import java.util.UUID;

public interface AcusarComunicadoUseCase {
    void acusar(UUID comunicadoId, UUID usuarioId);
}
