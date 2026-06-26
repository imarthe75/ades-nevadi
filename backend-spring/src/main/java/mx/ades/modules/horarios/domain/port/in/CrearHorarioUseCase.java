package mx.ades.modules.horarios.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para crear una entrada de horario para un grupo.
 * El origen puede ser MANUAL o ASC (importado desde aSc TimeTables via XML).
 *
 * @author ADES
 * @since 2026
 */
public interface CrearHorarioUseCase {

    record Command(
            UUID grupoId, UUID materiaId, UUID profesorId, UUID aulaId,
            UUID cicloEscolarId, Integer diaSemana,
            String horaInicio, String horaFin, String origen, String usuario) {
        public Command {
            if (grupoId == null) throw new IllegalArgumentException("grupo_id es requerido");
            if (diaSemana != null && (diaSemana < 1 || diaSemana > 5))
                throw new IllegalArgumentException("dia_semana debe estar entre 1 (Lunes) y 5 (Viernes)");
            if (origen == null || origen.isBlank()) origen = "MANUAL";
        }
    }

    UUID crear(Command cmd);
}
