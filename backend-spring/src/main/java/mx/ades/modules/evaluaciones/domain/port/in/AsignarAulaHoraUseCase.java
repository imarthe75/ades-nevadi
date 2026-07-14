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
            String username) {
        public Command {
            // aula_id y fecha son NOT NULL en ades_asignaciones_aula; sin esta validación,
            // aula_id ausente caía en un 409 "duplicado o referencia inválida" engañoso.
            if (aulaId == null)
                throw new IllegalArgumentException("aula_id es requerido");
            if (fecha == null)
                throw new IllegalArgumentException("fecha es requerida");
            if (slot == null)
                throw new IllegalArgumentException("hora_inicio y hora_fin son requeridas");
        }
    }

    UUID ejecutar(Command command);
}
