package mx.ades.modules.asistencias.domain.port.in;

import mx.ades.modules.asistencias.domain.model.Asistencia;

import java.util.List;
import java.util.UUID;

public interface ConsultarAsistenciasPorClaseUseCase {
    List<Asistencia> ejecutar(UUID claseId);
}
