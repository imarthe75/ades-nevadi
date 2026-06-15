package mx.ades.modules.expediente.domain.port.in;

import mx.ades.modules.expediente.domain.model.CalificacionExtra;

import java.time.LocalDate;
import java.util.UUID;

public interface CalificarExtraordinarioUseCase {

    record Command(
            UUID extraordinarioId,
            CalificacionExtra calificacion,
            boolean acredita,
            LocalDate fechaExamen,
            String username) {}

    void ejecutar(Command command);
}
