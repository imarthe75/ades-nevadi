package mx.ades.modules.conducta.domain.port.out;

import mx.ades.modules.conducta.domain.port.in.AplicarSancionCommand;

import java.util.UUID;

public interface SancionRepositoryPort {
    /** Persiste la sanción y devuelve su UUID generado. */
    UUID guardar(UUID estudianteId, AplicarSancionCommand command);
}
