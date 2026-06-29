package mx.ades.modules.horarios.solver;

import java.time.LocalTime;
import java.util.UUID;

public record HorarioTimeslot(
        UUID id,
        Integer diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        String turno) {
}