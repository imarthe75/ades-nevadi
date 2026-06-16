package mx.ades.modules.disponibilidad.domain.port.out;

import mx.ades.modules.disponibilidad.DisponibilidadDocente;
import mx.ades.modules.disponibilidad.domain.port.in.GuardarDisponibilidadUseCase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface DisponibilidadRepositoryPort {
    List<Map<String, Object>> list(UUID profesorId, UUID cicloEscolarId, String q);
    void softDeleteByProfesor(UUID profesorId, UUID cicloEscolarId);
    void createSlot(UUID profesorId, UUID cicloEscolarId, GuardarDisponibilidadUseCase.Slot slot, String usuario);
    void updateProfesorHoras(UUID profesorId, Double horasSemanaMax, Double horasFrenteGrupo, String usuario);
    Optional<DisponibilidadDocente> findById(UUID id);
    DisponibilidadDocente save(DisponibilidadDocente slot);
    List<Map<String, Object>> resumen(UUID profesorId, UUID cicloEscolarId);
    Map<String, Object> getProfesorHoras(UUID profesorId);
    List<Map<String, Object>> cobertura(UUID cicloId);
}
