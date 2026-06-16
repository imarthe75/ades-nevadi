package mx.ades.modules.entregas.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.entregas.domain.port.out.EntregaRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
