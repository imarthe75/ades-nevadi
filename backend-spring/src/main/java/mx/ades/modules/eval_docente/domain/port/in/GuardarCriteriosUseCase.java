package mx.ades.modules.eval_docente.domain.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GuardarCriteriosUseCase {

    record CriterioCalificacion(UUID criterioId, Integer calificacion, String observacion) {
        public CriterioCalificacion {
            if (criterioId == null) throw new IllegalArgumentException("criterio_id es requerido");
        }
    }

    record Command(UUID evalId, List<CriterioCalificacion> criterios) {
        public Command {
            if (evalId == null) throw new IllegalArgumentException("eval_id es requerido");
            if (criterios == null || criterios.isEmpty())
                throw new IllegalArgumentException("criterios no pueden estar vacíos");
        }
    }

    Map<String, Object> guardar(Command cmd);
}
