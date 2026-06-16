package mx.ades.modules.personal_admin.domain.port.out;

import mx.ades.modules.personal_admin.domain.port.in.RegistrarPersonalAdminUseCase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PersonalAdminRepositoryPort {
    List<Map<String, Object>> list(UUID plantelId, String tipoRol, String buscar);
    Optional<Map<String, Object>> findById(UUID id);
    Optional<UUID> findPersonaId(UUID id);
    UUID createPersona(Map<String, Object> persona, String usuario);
    UUID createEmpleado(UUID personaId, UUID plantelId, Map<String, Object> laborales, String usuario);
    void updatePersona(UUID personaId, Map<String, Object> persona, String usuario);
    void updateEmpleado(UUID id, Map<String, Object> laborales, String usuario);
    int softDelete(UUID id);
    Map<String, Object> fetchById(UUID id);
}
