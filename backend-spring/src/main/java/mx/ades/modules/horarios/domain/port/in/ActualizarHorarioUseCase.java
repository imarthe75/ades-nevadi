package mx.ades.modules.horarios.domain.port.in;

import java.util.UUID;

public interface ActualizarHorarioUseCase {

    record Command(
            UUID horarioId, UUID materiaId, UUID profesorId, UUID aulaId,
            Integer diaSemana, String horaInicio, String horaFin,
            String origen, String motivoCambio, String usuario) {
        public Command {
            if (horarioId == null) throw new IllegalArgumentException("horario_id es requerido");
            if (diaSemana != null && (diaSemana < 0 || diaSemana > 6))
                throw new IllegalArgumentException("dia_semana debe estar entre 0 (Lunes) y 6 (Domingo)");
        }
    }

    void actualizar(Command cmd);
}
