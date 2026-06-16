package mx.ades.modules.comunicados.domain.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ProgramarSiguienteUseCase {
    LocalDateTime programarSiguiente(UUID comunicadoId);
}
