package mx.ades.modules.eval_docente.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo eval_docente.
 * <p>Expone el catálogo de criterios de evaluación y el resumen consolidado por tipo de evaluador
 * para un profesor, con promedio global calculado en la capa de servicio.</p>
 *
 * @author ADES
 * @since 2026
 */
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
