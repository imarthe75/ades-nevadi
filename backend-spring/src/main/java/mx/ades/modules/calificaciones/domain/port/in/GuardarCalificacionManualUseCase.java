package mx.ades.modules.calificaciones.domain.port.in;

import mx.ades.modules.calificaciones.domain.model.Calificacion;

public interface GuardarCalificacionManualUseCase {
    Calificacion ejecutar(Calificacion calificacion);
}
