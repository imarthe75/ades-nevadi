package mx.ades.modules.gradebook.domain.port.in;

import mx.ades.modules.gradebook.domain.model.AjusteManual;

import java.math.BigDecimal;
import java.util.UUID;

public interface AplicarAjusteUseCase {

    record Command(UUID calPeriodoId, AjusteManual ajuste, String username, boolean esAdmin) {}

    record Result(BigDecimal calificacionFinal) {}

    Result ejecutar(Command command);
}
