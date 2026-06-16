package mx.ades.modules.cierre.domain.port.out;

import java.util.Optional;
import java.util.UUID;

public interface CierreRepositoryPort {

    Optional<String> fetchEstado(UUID cicloId);

    void marcarCerrado(UUID cicloId);
}
