package mx.ades.modules.horarios.solver;

import java.time.LocalTime;

public record HorarioTimeslot(
        Integer diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        String turno) {
}