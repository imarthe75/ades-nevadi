package mx.ades.modules.entregas.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface SubirEntregaUseCase {

    record Command(UUID tareaId, UUID alumnoId, String comentario, String archivoUrl, String usuario) {
        public Command {
            if (tareaId == null) throw new IllegalArgumentException("tarea_id es requerido");
            if (alumnoId == null) throw new IllegalArgumentException("alumno_id es requerido");
        }
    }

    Map<String, Object> subir(Command cmd);
}
