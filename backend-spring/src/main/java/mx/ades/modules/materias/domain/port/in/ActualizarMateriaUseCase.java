package mx.ades.modules.materias.domain.port.in;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface ActualizarMateriaUseCase {

    record Command(
            UUID materiaId,
            String nombreMateria,
            String claveMateria,
            UUID nivelEducativoId,
            BigDecimal horasSemana,
            Boolean esIngles,
            Boolean isActive
    ) {
        public Command {
            if (materiaId == null)
                throw new IllegalArgumentException("El ID de la materia es requerido");
            if (nombreMateria == null || nombreMateria.isBlank())
                throw new IllegalArgumentException("El nombre de la materia es requerido");
        }
    }

    Map<String, Object> actualizar(Command cmd);
}
