package mx.ades.modules.entregas.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para calificar una entrega de tarea en el módulo entregas.
 * <p>La calificación debe estar en el rango [0, 100]. El estado de la entrega pasa a CALIFICADA.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CalificarEntregaUseCase {

    record Command(UUID entregaId, Double calificacion, String comentario, UUID calificadoPor, String usuario) {
        public Command {
            if (entregaId == null) throw new IllegalArgumentException("entrega_id es requerido");
            if (calificacion != null && (calificacion < 0 || calificacion > 100))
                throw new IllegalArgumentException("calificacion debe estar entre 0 y 100");
        }
    }

    Map<String, Object> calificar(Command cmd);
}
