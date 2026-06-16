package mx.ades.modules.esquemas_ponderacion.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.esquemas_ponderacion.domain.port.out.EsquemaRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EsquemaQueryService {

    private final EsquemaRepositoryPort repository;

    public List<Map<String, Object>> listar(UUID nivelEducativoId, UUID materiaId) {
        return repository.list(nivelEducativoId, materiaId);
    }

    public Map<String, Object> efectivo(UUID materiaId) {
        return repository.efectivo(materiaId);
    }
}
