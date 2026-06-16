package mx.ades.modules.admin.domain.port.in;

import java.util.UUID;

public interface EvaluarPromocionUseCase {

    Object ejecutar(Command cmd);

    record Command(UUID cicloId, UUID plantelId, String usuario) {
        public Command {
            if (cicloId == null) throw new IllegalArgumentException("cicloId es requerido");
            if (usuario == null || usuario.isBlank()) throw new IllegalArgumentException("usuario es requerido");
        }
    }
}
