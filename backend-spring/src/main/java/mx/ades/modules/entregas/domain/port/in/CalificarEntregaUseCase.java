package mx.ades.modules.entregas.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface CalificarEntregaUseCase {

    record Command(UUID entregaId, Double calificacion, String comentario, UUID calificadoPor, String usuario) {
        public Command {
            if (entregaId == null) throw new IllegalArgumentException("entrega_id es requerido");
            if (calificacion != null && (calificacion < 0 || calificacion > 100))
                throw new IllegalArgumentException("calificacion debe estar entre 0 y 100");
        }
    }

    Map<String, Object> calificar(Command cmd);
}
