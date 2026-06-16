package mx.ades.modules.badges.domain.port.in;

import java.util.UUID;

public interface AutoEvaluarBadgesUseCase {

    record Result(int badgesEvaluados, int totalOtorgados) {}

    Result autoEvaluar(UUID cicloId);
}
