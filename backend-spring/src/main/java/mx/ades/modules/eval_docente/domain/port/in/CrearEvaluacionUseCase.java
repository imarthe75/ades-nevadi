package mx.ades.modules.eval_docente.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface CrearEvaluacionUseCase {

    record Command(UUID profesorId, UUID cicloEscolarId, UUID evaluadorId,
                   String tipoEvaluador, String comentarios, String usuario) {
        public Command {
            if (profesorId == null) throw new IllegalArgumentException("profesor_id es requerido");
            if (cicloEscolarId == null) throw new IllegalArgumentException("ciclo_escolar_id es requerido");
            if (tipoEvaluador == null || tipoEvaluador.isBlank())
                throw new IllegalArgumentException("tipo_evaluador es requerido");
        }
    }

    Map<String, Object> crear(Command cmd);
}
