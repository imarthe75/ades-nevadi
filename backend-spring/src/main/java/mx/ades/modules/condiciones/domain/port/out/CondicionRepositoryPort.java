package mx.ades.modules.condiciones.domain.port.out;

import mx.ades.modules.condiciones.CondicionCronica;
import mx.ades.modules.condiciones.domain.model.TipoCondicion;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CondicionRepositoryPort {

    CondicionCronica save(CondicionCronica condicion);

    Optional<CondicionCronica> findActiveById(UUID id);

    List<Map<String, Object>> list(UUID alumnoId, String tipoCondicion, boolean soloActivas);

    List<Map<String, Object>> alertaEmergencia(UUID alumnoId);
}
