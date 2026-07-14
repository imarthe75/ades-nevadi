package mx.ades.modules.materias.domain.port.in;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear una nueva materia en el catálogo.
 *
 * @author ADES
 * @since 2026
 */
public interface CrearMateriaUseCase {

    /** Valores permitidos por el CHECK chk_tipo_materia en ades_materias. */
    Set<String> TIPOS_MATERIA_VALIDOS = Set.of(
            "OFICIAL_SEP_PRIMARIA", "OFICIAL_SEP_SECUNDARIA", "OFICIAL_UAEMEX_PREP",
            "NEVADI_FORMATIVA", "NEVADI_ENRIQUECIMIENTO", "NEVADI_ESPECIALIZADA");

    /** Valores permitidos por el CHECK ck_materias_campo_formativo (NULL también es válido). */
    Set<String> CAMPOS_FORMATIVOS_VALIDOS = Set.of(
            "LENGUAJES", "SABERES_PENSAMIENTO_CIENTIFICO", "ETICA_NATURALEZA_SOCIEDADES", "HUMANO_COMUNITARIO");

    record Command(
            String nombreMateria,
            String claveMateria,
            UUID nivelEducativoId,
            String tipoMateria,
            String campoFormativo,
            BigDecimal horasSemana,
            Boolean esIngles
    ) {
        public Command {
            if (nombreMateria == null || nombreMateria.isBlank())
                throw new IllegalArgumentException("El nombre de la materia es requerido");
            if (nivelEducativoId == null)
                throw new IllegalArgumentException("El nivel educativo es requerido");
            if (tipoMateria == null || tipoMateria.isBlank())
                throw new IllegalArgumentException("El tipo de materia es requerido");
            if (!TIPOS_MATERIA_VALIDOS.contains(tipoMateria))
                throw new IllegalArgumentException("tipo_materia inválido: " + tipoMateria);
            if (campoFormativo != null && !campoFormativo.isBlank() && !CAMPOS_FORMATIVOS_VALIDOS.contains(campoFormativo))
                throw new IllegalArgumentException("campo_formativo inválido: " + campoFormativo);
        }
    }

    Map<String, Object> crear(Command cmd);
}
