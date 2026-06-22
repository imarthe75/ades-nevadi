package mx.ades.modules.biblioteca.domain.port.in;

import java.util.UUID;

public interface EliminarLibroUseCase {

    void eliminar(UUID libroId, int nivelAcceso);
}
