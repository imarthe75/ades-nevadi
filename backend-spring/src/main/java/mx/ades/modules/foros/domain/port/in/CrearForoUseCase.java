package mx.ades.modules.foros.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface CrearForoUseCase {

    record Command(
            String nombre,
            String descripcion,
            String tipo,
            UUID grupoId,
            UUID plantelId,
            UUID materiaId,
            Boolean esModerado,
            UUID creadoPor,
            Integer nivelAcceso
    ) {
        public Command {
            if (nombre == null || nombre.isBlank())
                throw new IllegalArgumentException("El nombre del foro es requerido");
            if (nivelAcceso != null && nivelAcceso > 3)
                throw new IllegalArgumentException("Se requiere Coordinador o superior");
        }
    }

    Map<String, Object> crear(Command cmd);
}
