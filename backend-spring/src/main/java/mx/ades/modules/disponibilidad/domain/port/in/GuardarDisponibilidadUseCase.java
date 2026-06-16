package mx.ades.modules.disponibilidad.domain.port.in;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

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
