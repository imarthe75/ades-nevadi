package mx.ades.modules.capacitaciones.domain.port.out;

import mx.ades.modules.capacitaciones.CapacitacionDocente;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CapacitacionRepositoryPort {

    CapacitacionDocente save(CapacitacionDocente cap);

    Optional<CapacitacionDocente> findActiveById(UUID id);

    List<Map<String, Object>> list(UUID docenteId, String tipo, String modalidad, Boolean validado, String q);

    List<Map<String, Object>> resumen(UUID docenteId);
}
