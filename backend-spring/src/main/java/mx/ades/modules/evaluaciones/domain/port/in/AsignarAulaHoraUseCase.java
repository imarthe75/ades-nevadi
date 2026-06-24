package mx.ades.modules.evaluaciones.domain.port.in;

import mx.ades.modules.evaluaciones.domain.model.SlotHorario;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para asignar un aula a un horario específico en el módulo evaluaciones.
 * <p>Verifica conflictos de reserva antes de persistir la asignación y publica {@code AulaAsignadaEvent}.</p>
 *
 * @author ADES
 * @since 2026
 */
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
