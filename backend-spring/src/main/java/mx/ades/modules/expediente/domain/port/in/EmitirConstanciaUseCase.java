package mx.ades.modules.expediente.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

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
