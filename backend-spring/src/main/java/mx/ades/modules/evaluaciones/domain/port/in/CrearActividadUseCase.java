package mx.ades.modules.evaluaciones.domain.port.in;

import mx.ades.modules.evaluaciones.domain.model.TipoItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear una actividad evaluable (tarea, examen, proyecto, etc.)
 * en el módulo evaluaciones.
 * <p>Al crear la actividad se generan automáticamente los slots de entrega (PENDIENTE)
 * para todos los alumnos inscritos en el grupo. Publica {@code ActividadCreadaEvent}.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface CrearActividadUseCase {

    record Command(
            String titulo,
            String descripcion,
            UUID grupoId,
            UUID materiaId,
            UUID temaId,
            UUID periodoEvaluacionId,
            LocalDate fechaAsignacion,
            LocalDate fechaEntrega,
            BigDecimal puntajeMaximo,
            TipoItem tipoItem,
            boolean permiteEntregaTarde,
            String instruccionesUrl,
            String creadorUsername) {
    }

    record Result(UUID tareaId, int slotsCreados) {
    }

    Result ejecutar(Command command);
}
