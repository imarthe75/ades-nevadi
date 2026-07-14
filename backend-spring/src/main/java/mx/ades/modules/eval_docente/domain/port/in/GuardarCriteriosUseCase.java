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
            // ades_eval_docente_criterios.calificacion es SMALLINT NOT NULL. La escala
            // vive por criterio en ades_criterios_eval_docente.escala_min/escala_max
            // (default 1-5, sin mecanismo de UI para sobreescribirla — ver
            // EvalDocenteController: "la escala de calificación por criterio es 1-5").
            // Se valida aquí para devolver 400 claro en vez de una violación NOT NULL/
            // fuera-de-rango silenciosa a nivel BD (hallazgo de auditoría).
            if (calificacion == null) throw new IllegalArgumentException("calificacion es requerida");
            if (calificacion < 1 || calificacion > 5)
                throw new IllegalArgumentException("calificacion debe estar entre 1 y 5");
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
