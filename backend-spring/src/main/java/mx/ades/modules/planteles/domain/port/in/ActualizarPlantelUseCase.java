package mx.ades.modules.planteles.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface ActualizarPlantelUseCase {

    record Command(
            UUID plantelId,
            String nombrePlantel,
            UUID escuelaId,
            String claveCt,
            UUID estatusId,
            Boolean isActive
    ) {
        public Command {
            if (plantelId == null)
                throw new IllegalArgumentException("El ID del plantel es requerido");
            if (nombrePlantel == null || nombrePlantel.isBlank())
                throw new IllegalArgumentException("El nombre del plantel es requerido");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}
