package mx.ades.modules.disponibilidad.domain.port.in;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para guardar (reemplazar) la disponibilidad horaria semanal
 * de un profesor en el módulo disponibilidad.
 * <p>El proceso realiza soft-delete de los slots previos y crea los nuevos en una sola operación.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface GuardarDisponibilidadUseCase {

    record Slot(Integer diaSemana, LocalTime horaInicio, LocalTime horaFin,
                Boolean disponible, String motivoNoDisponible) {}

    record Command(UUID profesorId, UUID cicloEscolarId, List<Slot> slots,
                   Double horasSemanaMax, Double horasFrenteGrupo, String usuario) {
        public Command {
            if (profesorId == null) throw new IllegalArgumentException("profesor_id es requerido");
            if (slots == null || slots.isEmpty()) throw new IllegalArgumentException("slots no puede estar vacío");
        }
    }

    void guardar(Command cmd);
}
