package mx.ades.modules.evaluaciones.domain.port.out;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;

import java.time.LocalDate;
import java.util.UUID;

public interface AsignacionAulaRepositoryPort {

    boolean existeConflicto(UUID aulaId, LocalDate fecha, SlotHorario slot);

    UUID guardar(UUID claseId, UUID aulaId, LocalDate fecha, SlotHorario slot,
                 String observaciones, String username);
}
