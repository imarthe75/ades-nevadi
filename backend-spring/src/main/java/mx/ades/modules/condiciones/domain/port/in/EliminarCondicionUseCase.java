package mx.ades.modules.condiciones.domain.port.in;

import java.util.UUID;

public interface EliminarCondicionUseCase {

    void eliminar(UUID condicionId, int nivelAcceso);
}
