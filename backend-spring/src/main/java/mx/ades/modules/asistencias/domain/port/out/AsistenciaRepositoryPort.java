package mx.ades.modules.asistencias.domain.port.out;

import mx.ades.modules.asistencias.domain.model.Asistencia;

import java.util.List;
import java.util.UUID;

public interface AsistenciaRepositoryPort {
    List<Asistencia> findByClaseId(UUID claseId);
    void guardarMasivo(List<Asistencia> asistencias);
    long contarAsistenciasByEstudiante(UUID estudianteId, UUID grupoId);
}
