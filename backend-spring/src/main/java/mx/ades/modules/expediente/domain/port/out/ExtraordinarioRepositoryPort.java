package mx.ades.modules.expediente.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface ExtraordinarioRepositoryPort {

    boolean existeActivo(UUID extraordinarioId);

    void calificar(UUID extraordinarioId, BigDecimal calificacion,
                   boolean acredita, LocalDate fechaExamen);
}
