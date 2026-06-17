package mx.ades.modules.aulas.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface ActualizarAulaUseCase {

    record Command(
            UUID aulaId,
            String nombreAula,
            UUID plantelId,
            String tipoAula,
            Integer capacidadAlumnos,
            Boolean isActive
    ) {
        public Command {
            if (aulaId == null)
                throw new IllegalArgumentException("El ID del aula es requerido");
            if (nombreAula == null || nombreAula.isBlank())
                throw new IllegalArgumentException("El nombre del aula es requerido");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}
