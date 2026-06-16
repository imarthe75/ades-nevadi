package mx.ades.modules.horarios.domain.port.in;

import java.util.UUID;

public interface CrearHorarioUseCase {

    record Command(
            UUID grupoId, UUID materiaId, UUID profesorId, UUID aulaId,
            UUID cicloEscolarId, Integer diaSemana,
            String horaInicio, String horaFin, String origen, String usuario) {
        public Command {
            if (grupoId == null) throw new IllegalArgumentException("grupo_id es requerido");
            if (diaSemana != null && (diaSemana < 0 || diaSemana > 6))
                throw new IllegalArgumentException("dia_semana debe estar entre 0 (Lunes) y 6 (Domingo)");
            if (origen == null || origen.isBlank()) origen = "MANUAL";
        }
    }

    UUID crear(Command cmd);
}
