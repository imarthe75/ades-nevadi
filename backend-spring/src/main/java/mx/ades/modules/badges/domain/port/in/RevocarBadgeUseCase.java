package mx.ades.modules.badges.domain.port.in;

import java.util.UUID;

public interface RevocarBadgeUseCase {
    void revocar(UUID badgeId, UUID estudianteId, UUID cicloId);
}
