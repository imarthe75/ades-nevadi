package mx.ades.modules.justificaciones.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.justificaciones.domain.port.out.JustificacionRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JustificacionQueryService {

    private final JustificacionRepositoryPort repo;

    public List<Map<String, Object>> list(UUID estudianteId, String estado, UUID grupoId) {
        return repo.list(estudianteId, estado, grupoId);
    }

    public Map<String, Object> findById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Justificación no encontrada"));
    }
}
