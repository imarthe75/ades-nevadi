package mx.ades.modules.calificaciones.domain.port.out;

import mx.ades.modules.calificaciones.domain.model.Calificacion;

import java.util.List;
import java.util.UUID;

public interface CalificacionRepositoryPort {
    /** Invoca calcular_calificacion_periodo() en PostgreSQL y devuelve el resultado actualizado. */
    Calificacion calcular(UUID inscripcionId, UUID materiaId, UUID periodoId);

    Calificacion guardar(Calificacion calificacion);

    List<Calificacion> findByEstudianteId(UUID estudianteId);
}
