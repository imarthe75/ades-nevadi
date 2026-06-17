package mx.ades.modules.planteles.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface CrearPlantelUseCase {

    record Command(
            String nombrePlantel,
            UUID escuelaId,
            String claveCt,
            UUID estatusId
    ) {
        public Command {
            if (nombrePlantel == null || nombrePlantel.isBlank())
                throw new IllegalArgumentException("El nombre del plantel es requerido");
            if (escuelaId == null)
                throw new IllegalArgumentException("La escuela es requerida");
        }
    }

    Map<String, Object> crear(Command cmd);
}
