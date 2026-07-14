package mx.ades.modules.eval_docente.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear una evaluación docente 360° en el módulo eval_docente.
 * <p>La evaluación se crea en estado BORRADOR; los criterios se capturan posteriormente
 * mediante {@link GuardarCriteriosUseCase}.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CrearEvaluacionUseCase {

    record Command(UUID profesorId, UUID cicloEscolarId, UUID evaluadorId,
                   String tipoEvaluador, String comentarios, String usuario) {
        public Command {
            if (profesorId == null) throw new IllegalArgumentException("profesor_id es requerido");
            if (cicloEscolarId == null) throw new IllegalArgumentException("ciclo_escolar_id es requerido");
            // evaluador_id es NOT NULL en ades_evaluacion_docente (FK a ades_usuarios) —
            // faltaba esta validación (hallazgo de auditoría de consistencia BD↔backend).
            if (evaluadorId == null) throw new IllegalArgumentException("evaluador_id es requerido");
            if (tipoEvaluador == null || tipoEvaluador.isBlank())
                throw new IllegalArgumentException("tipo_evaluador es requerido");
        }
    }

    Map<String, Object> crear(Command cmd);
}
