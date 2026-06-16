package mx.ades.modules.badges.domain.port.in;

import java.util.UUID;

public interface OtorgarBadgeUseCase {

    record Command(UUID badgeId, UUID estudianteId, UUID cicloId, String motivo, UUID otorgadoPor) {
        public Command {
            if (badgeId == null)     throw new IllegalArgumentException("badge_id es requerido");
            if (estudianteId == null) throw new IllegalArgumentException("estudiante_id es requerido");
        }
    }

    /** @return true if newly granted, false if already existed (duplicate) */
    boolean otorgar(Command cmd);
}
