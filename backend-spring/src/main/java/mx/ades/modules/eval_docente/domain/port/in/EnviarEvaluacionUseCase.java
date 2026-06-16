package mx.ades.modules.eval_docente.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface EnviarEvaluacionUseCase {

    record Command(UUID evalId, String usuario) {
        public Command {
            if (evalId == null) throw new IllegalArgumentException("eval_id es requerido");
        }
    }

    Map<String, Object> enviar(Command cmd);
}
