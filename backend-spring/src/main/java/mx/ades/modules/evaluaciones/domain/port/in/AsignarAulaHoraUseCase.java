package mx.ades.modules.evaluaciones.domain.port.in;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;

import java.time.LocalDate;
import java.util.UUID;

public interface AsignarAulaHoraUseCase {

    record Command(
            UUID claseId,
            UUID aulaId,
            LocalDate fecha,
            SlotHorario slot,
            String observaciones,
            String username) {}

    UUID ejecutar(Command command);
}
