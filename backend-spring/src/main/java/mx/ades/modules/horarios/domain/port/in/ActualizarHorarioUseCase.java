package mx.ades.modules.horarios.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para actualizar un horario existente.
 * Soporta cambios de materia, profesor, aula, día y hora; registra motivo de cambio
 * para trazabilidad (compatible con round-trip XML aSc TimeTables).
 *
 * @author ADES
 * @since 2026
 */
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
