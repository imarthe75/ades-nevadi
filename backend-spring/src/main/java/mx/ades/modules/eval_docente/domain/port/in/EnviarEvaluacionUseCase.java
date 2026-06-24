package mx.ades.modules.eval_docente.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para enviar (finalizar) una evaluación docente 360°
 * en el módulo eval_docente.
 * <p>Cambia el estado de BORRADOR a ENVIADA; solo aplicable mientras el estado sea BORRADOR.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface EnviarEvaluacionUseCase {

    record Command(UUID evalId, String usuario) {
        public Command {
            if (evalId == null) throw new IllegalArgumentException("eval_id es requerido");
        }
    }

    Map<String, Object> enviar(Command cmd);
}
