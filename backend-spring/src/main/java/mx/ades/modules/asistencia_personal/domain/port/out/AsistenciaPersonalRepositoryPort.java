package mx.ades.modules.asistencia_personal.domain.port.out;

import mx.ades.modules.asistencia_personal.domain.port.in.ActualizarAsistenciaUseCase;
import mx.ades.modules.asistencia_personal.domain.port.in.RegistrarAsistenciaUseCase;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AsistenciaPersonalRepositoryPort {
    List<Map<String, Object>> list(UUID personaId, LocalDate fechaInicio, LocalDate fechaFin, String tipoJornada, String q);
    boolean existeRegistro(UUID personaId, LocalDate fecha);
    void insert(RegistrarAsistenciaUseCase.Command cmd);
    void update(RegistrarAsistenciaUseCase.Command cmd);
    Optional<Map<String, Object>> findById(UUID id);
    Map<String, Object> findByPersonaFecha(UUID personaId, LocalDate fecha);
    void patch(ActualizarAsistenciaUseCase.Command cmd);
    Map<String, Object> fetchById(UUID id);
    List<Map<String, Object>> reporte(UUID personaId, int mes, int anio);
    void softDelete(UUID id, String usuario);
}
