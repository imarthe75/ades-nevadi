package mx.ades.modules.disponibilidad.domain.port.in;

import java.util.UUID;

public interface EliminarSlotUseCase {
    void eliminar(UUID slotId);
}
