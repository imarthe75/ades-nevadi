package mx.ades.modules.conducta.domain.port.in;

import java.util.UUID;

public interface AplicarSancionUseCase {
    UUID ejecutar(AplicarSancionCommand command);
}
