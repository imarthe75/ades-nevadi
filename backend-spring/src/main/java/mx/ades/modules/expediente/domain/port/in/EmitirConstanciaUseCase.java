package mx.ades.modules.expediente.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para emitir una constancia escolar en el módulo expediente.
 * <p>Genera un folio único con formato {@code TIP-AAAA-NNNN} y persiste en {@code ades_constancias}.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EmitirConstanciaUseCase {

    record Command(
            UUID estudianteId,
            String tipoConstancia,
            UUID cicloEscolarId,
            String solicitadaPor,
            String proposito,
            LocalDate fechaVencimiento,
            String observaciones,
            UUID emitidaPorId,
            String username) {}

    record Result(UUID constanciaId, String folio) {}

    Result ejecutar(Command command);
}
