package mx.ades.modules.medico.domain.port.out;

import mx.ades.modules.medico.domain.port.in.RegistrarPersonalSaludUseCase;
import mx.ades.modules.medico.domain.port.in.ActualizarPersonalSaludUseCase;

import java.util.Map;
import java.util.UUID;

public interface PersonalSaludRepositoryPort {

    UUID insert(RegistrarPersonalSaludUseCase.Command cmd);

    void update(UUID saludId, ActualizarPersonalSaludUseCase.Command cmd);

    int softDelete(UUID saludId);

    Map<String, Object> fetchById(UUID saludId);
}
