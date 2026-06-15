package mx.ades.modules.calificaciones.domain.port.in;

import java.util.UUID;

public interface CalcularCalificacionPeriodoUseCase {
    void ejecutar(UUID estudianteId, UUID inscripcionId, UUID materiaId, UUID periodoId);
}
