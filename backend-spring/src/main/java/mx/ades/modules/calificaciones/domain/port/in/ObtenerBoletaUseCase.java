package mx.ades.modules.calificaciones.domain.port.in;

import mx.ades.modules.calificaciones.domain.model.Calificacion;

import java.util.List;
import java.util.UUID;

public interface ObtenerBoletaUseCase {
    List<Calificacion> ejecutar(UUID estudianteId);
}
