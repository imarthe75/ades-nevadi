package mx.ades.modules.reinscripcion.domain.port.out;

import java.util.UUID;

public interface ReinscripcionRepositoryPort {

    void procesarAccion(UUID registroId, String estado, String razonRechazo, UUID procesadoPor);
}
