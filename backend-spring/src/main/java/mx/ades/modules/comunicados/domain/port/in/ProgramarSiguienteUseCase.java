package mx.ades.modules.comunicados.domain.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para calcular y persistir la siguiente fecha de envío
 * de un comunicado recurrente en el módulo comunicados.
 *
 * @author ADES
 * @since 2026
 */
public interface ProgramarSiguienteUseCase {
    LocalDateTime programarSiguiente(UUID comunicadoId);
}
