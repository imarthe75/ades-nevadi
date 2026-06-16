package mx.ades.modules.expediente_laboral.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.expediente_laboral.domain.port.out.ExpedienteLaboralRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpedienteLaboralQueryService {

    private final ExpedienteLaboralRepositoryPort repository;

    public List<Map<String, Object>> listar(UUID personaId, String tipoContrato, String q) {
        return repository.list(personaId, tipoContrato, q);
    }

    public Map<String, Object> detalle(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expediente laboral no encontrado: " + id));
    }
}
