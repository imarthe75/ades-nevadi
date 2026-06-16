package mx.ades.modules.eval_docente.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvalDocenteQueryService {

    private final EvalDocenteRepositoryPort repository;

    public List<Map<String, Object>> listarCriterios() {
        return repository.listarCriterios();
    }

    public Map<String, Object> resumenProfesor(UUID profesorId, UUID cicloId) {
        return repository.resumenProfesor(profesorId, cicloId);
    }
}
