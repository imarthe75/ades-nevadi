package mx.ades.modules.materias.domain.port.in;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para actualizar los datos de una materia existente.
 *
 * @author ADES
 * @since 2026
 */
public interface ActualizarMateriaUseCase {

    record Command(
            UUID materiaId,
            String nombreMateria,
            String claveMateria,
            UUID nivelEducativoId,
            String tipoMateria,
            String campoFormativo,
            BigDecimal horasSemana,
            Boolean esIngles,
            Boolean isActive
    ) {
        public Command {
            if (materiaId == null)
                throw new IllegalArgumentException("El ID de la materia es requerido");
            if (nombreMateria == null || nombreMateria.isBlank())
                throw new IllegalArgumentException("El nombre de la materia es requerido");
            if (tipoMateria != null && !tipoMateria.isBlank()
                    && !CrearMateriaUseCase.TIPOS_MATERIA_VALIDOS.contains(tipoMateria))
                throw new IllegalArgumentException("tipo_materia inválido: " + tipoMateria);
            if (campoFormativo != null && !campoFormativo.isBlank()
                    && !CrearMateriaUseCase.CAMPOS_FORMATIVOS_VALIDOS.contains(campoFormativo))
                throw new IllegalArgumentException("campo_formativo inválido: " + campoFormativo);
        }
    }

    Map<String, Object> actualizar(Command cmd);
}
