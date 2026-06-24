package mx.ades.modules.evaluaciones.domain.event;

import mx.ades.modules.evaluaciones.domain.model.TipoItem;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio publicado cuando una actividad (tarea/examen/proyecto) es creada
 * y sus slots de entrega son generados para todos los alumnos del grupo.
 *
 * @author ADES
 * @since 2026
 */
public record ActividadCreadaEvent(
        UUID tareaId,
        UUID grupoId,
        UUID materiaId,
        TipoItem tipoItem,
        int slotsCreados,
        Instant ocurridoEn) {
}
