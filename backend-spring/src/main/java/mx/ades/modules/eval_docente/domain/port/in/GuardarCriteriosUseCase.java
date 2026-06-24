package mx.ades.modules.eval_docente.domain.port.in;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para guardar o actualizar los criterios calificados
 * de una evaluación docente 360° en el módulo eval_docente.
 * <p>Usa upsert por (evaluacion_id, criterio_id) y recalcula automáticamente la calificación global.</p>
 *
 * @author ADES
 * @since 2026
 */
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
