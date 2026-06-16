package mx.ades.modules.badges.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.badges.Badge;
import mx.ades.modules.badges.domain.port.out.BadgeRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BadgeQueryService {

    private final BadgeRepositoryPort repo;

    public List<Map<String, Object>> listar(String tipo, UUID plantelId) {
        return repo.list(tipo, plantelId);
    }

    public Map<String, Object> detalle(UUID id) {
        Badge b = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge no encontrado"));
        Map<String, Object> extra = repo.detalle(id);
        Map<String, Object> out = new HashMap<>();
        out.put("id", b.getId());
        out.put("nombre", b.getNombre());
        out.put("descripcion", b.getDescripcion());
        out.put("icono", b.getIcono());
        out.put("color", b.getColor());
        out.put("tipo", b.getTipo());
        out.put("criterio_tipo", b.getCriterioTipo());
        out.put("criterio_metrica", b.getCriterioMetrica());
        out.put("criterio_valor", b.getCriterioValor());
        out.put("plantel_id", b.getPlantelId());
        out.putAll(extra);
        return out;
    }

    public List<Map<String, Object>> badgesAlumno(UUID estudianteId, UUID cicloId) {
        return repo.badgesAlumno(estudianteId, cicloId);
    }
}
