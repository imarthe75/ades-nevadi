package mx.ades.modules.materias.domain.port.in;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear una nueva materia en el catálogo.
 *
 * @author ADES
 * @since 2026
 */
public interface CrearMateriaUseCase {

    record Command(
            String nombreMateria,
            String claveMateria,
            UUID nivelEducativoId,
            BigDecimal horasSemana,
            Boolean esIngles
    ) {
        public Command {
            if (nombreMateria == null || nombreMateria.isBlank())
                throw new IllegalArgumentException("El nombre de la materia es requerido");
            if (nivelEducativoId == null)
                throw new IllegalArgumentException("El nivel educativo es requerido");
        }
    }

    Map<String, Object> crear(Command cmd);
}
