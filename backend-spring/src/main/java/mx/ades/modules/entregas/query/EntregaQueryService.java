package mx.ades.modules.entregas.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.entregas.domain.port.out.EntregaRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de lectura CQRS para el módulo entregas.
 * <p>Expone entregas de un alumno (con filtros opcionales de periodo y materia)
 * y lista de entregas pendientes de calificación por grupo.</p>
 *
 * @author ADES
 * @since 2026
 */
@Service
@RequiredArgsConstructor
public class EntregaQueryService {

    private final EntregaRepositoryPort repository;

    public List<Map<String, Object>> byAlumno(UUID alumnoId, UUID periodoId, UUID materiaId, boolean soloPendientes) {
        return repository.listByAlumno(alumnoId, periodoId, materiaId, soloPendientes);
    }

    public List<Map<String, Object>> pendientesByGrupo(UUID grupoId, UUID materiaId) {
        return repository.pendientesByGrupo(grupoId, materiaId);
    }
}
