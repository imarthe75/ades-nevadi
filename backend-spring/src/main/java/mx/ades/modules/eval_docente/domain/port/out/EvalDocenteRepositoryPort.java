package mx.ades.modules.eval_docente.domain.port.out;

import mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase;
import mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EvalDocenteRepositoryPort {
    UUID insertEvaluacion(CrearEvaluacionUseCase.Command cmd);
    Map<String, Object> fetchEvaluacion(UUID evalId);
    Optional<String> findEstatus(UUID evalId);
    void upsertCriterio(UUID evalId, GuardarCriteriosUseCase.CriterioCalificacion criterio);
    void recalcularGlobal(UUID evalId);
    int enviar(UUID evalId, String usuario);
    List<Map<String, Object>> listarCriterios();
    Map<String, Object> resumenProfesor(UUID profesorId, UUID cicloId);
}
