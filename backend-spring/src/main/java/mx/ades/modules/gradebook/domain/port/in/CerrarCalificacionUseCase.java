package mx.ades.modules.gradebook.domain.port.in;

import java.util.Set;
import java.util.UUID;

public interface CerrarCalificacionUseCase {

    Set<String> ROLES_AUTORIZADOS = Set.of(
            "ADMIN_GLOBAL", "ADMIN_PLANTEL", "DIRECTOR", "COORDINADOR_ACADEMICO");

    record Command(UUID calPeriodoId, String username, Set<String> roles) {
        public boolean tienePermiso() {
            return roles.stream().anyMatch(ROLES_AUTORIZADOS::contains);
        }
    }

    void ejecutar(Command command);
}
