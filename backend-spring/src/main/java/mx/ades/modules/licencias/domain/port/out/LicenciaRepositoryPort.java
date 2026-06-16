package mx.ades.modules.licencias.domain.port.out;

import mx.ades.modules.licencias.LicenciaPersonal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface LicenciaRepositoryPort {

    LicenciaPersonal save(LicenciaPersonal licencia);

    Optional<LicenciaPersonal> findActiveById(UUID id);

    List<Map<String, Object>> list(UUID personalId, String estado, String tipo, String q);
}
