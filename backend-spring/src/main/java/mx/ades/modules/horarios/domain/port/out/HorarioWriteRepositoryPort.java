package mx.ades.modules.horarios.domain.port.out;

import mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase;
import mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase;

import java.util.UUID;

public interface HorarioWriteRepositoryPort {

    UUID insert(CrearHorarioUseCase.Command cmd);

    boolean exists(UUID horarioId);

    void update(ActualizarHorarioUseCase.Command cmd);

    void softDelete(UUID horarioId);
}
