package mx.ades.modules.entregas.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface RegistrarExcusaUseCase {

    record Command(UUID entregaId, String motivo, String usuario) {
        public Command {
            if (entregaId == null) throw new IllegalArgumentException("entrega_id es requerido");
            if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("motivo es requerido");
        }
    }

    Map<String, Object> registrar(Command cmd);
}
