package mx.ades.modules.condiciones.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.condiciones.CondicionCronica;
import mx.ades.modules.condiciones.domain.port.out.CondicionRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CondicionQueryService {

    private final CondicionRepositoryPort repo;

    public List<Map<String, Object>> list(UUID alumnoId, String tipoCondicion, boolean soloActivas) {
        return repo.list(alumnoId, tipoCondicion, soloActivas);
    }

    public CondicionCronica findById(UUID id) {
        return repo.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Condición no encontrada"));
    }

    public List<Map<String, Object>> alertaEmergencia(UUID alumnoId) {
        return repo.alertaEmergencia(alumnoId);
    }
}
