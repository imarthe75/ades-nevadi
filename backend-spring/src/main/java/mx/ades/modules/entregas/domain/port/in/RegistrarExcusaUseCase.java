package mx.ades.modules.entregas.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para registrar una excusa sobre una entrega de tarea
 * en el módulo entregas.
 * <p>Cambia el estado de la entrega a EXCUSA y registra el motivo como comentario del profesor.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface RegistrarExcusaUseCase {

    record Command(UUID entregaId, String motivo, String usuario) {
        public Command {
            if (entregaId == null) throw new IllegalArgumentException("entrega_id es requerido");
            if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("motivo es requerido");
        }
    }

    Map<String, Object> registrar(Command cmd);
}
