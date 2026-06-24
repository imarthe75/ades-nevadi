package mx.ades.modules.entregas.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para que un alumno suba su entrega de tarea en el módulo entregas.
 * <p>Usa upsert: si ya existía una entrega previa para el par (tareaId, alumnoId), la actualiza.
 * El campo {@code archivoUrl} apunta al objeto almacenado en SeaweedFS/S3.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface SubirEntregaUseCase {

    record Command(UUID tareaId, UUID alumnoId, String comentario, String archivoUrl, String usuario) {
        public Command {
            if (tareaId == null) throw new IllegalArgumentException("tarea_id es requerido");
            if (alumnoId == null) throw new IllegalArgumentException("alumno_id es requerido");
        }
    }

    Map<String, Object> subir(Command cmd);
}
