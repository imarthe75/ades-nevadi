package mx.ades.modules.personal_admin.query;

import lombok.RequiredArgsConstructor;
import mx.ades.modules.personal_admin.domain.port.out.PersonalAdminRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersonalAdminQueryService {

    private final PersonalAdminRepositoryPort repository;

    public List<Map<String, Object>> listar(UUID plantelId, String tipoRol, String buscar) {
        return repository.list(plantelId, tipoRol, buscar);
    }

    public Map<String, Object> detalle(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Personal no encontrado: " + id));
    }
}
