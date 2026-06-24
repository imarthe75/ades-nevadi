package mx.ades.modules.badges.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: define el contrato para revocar un badge previamente otorgado
 * a un alumno en el dominio de badges.
 *
 * @author ADES
 * @since 2026
 */
public interface RevocarBadgeUseCase {
    void revocar(UUID badgeId, UUID estudianteId, UUID cicloId);
}
