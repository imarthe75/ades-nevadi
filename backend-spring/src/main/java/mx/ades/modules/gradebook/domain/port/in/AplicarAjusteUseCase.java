package mx.ades.modules.gradebook.domain.port.in;

import mx.ades.modules.gradebook.domain.model.AjusteManual;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para aplicar un ajuste manual a una calificación de periodo.
 * Las calificaciones cerradas solo pueden ser ajustadas por administradores.
 *
 * @author ADES
 * @since 2026
 */
public interface AplicarAjusteUseCase {

    record Command(UUID calPeriodoId, AjusteManual ajuste, String username, boolean esAdmin) {}

    record Result(BigDecimal calificacionFinal) {}

    Result ejecutar(Command command);
}
