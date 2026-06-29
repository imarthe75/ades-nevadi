package mx.ades.modules.horarios.solver;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardSoftScore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@PlanningSolution
@Getter
@Setter
public class HorarioPlan {

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeslotRange")
    private List<HorarioTimeslot> timeslots;

    @PlanningEntityCollectionProperty
    private List<HorarioLeccion> lecciones;

    @ProblemFactCollectionProperty
    private List<mx.ades.modules.horarios.config.HorarioRegla> reglas;

    @ProblemFactCollectionProperty
    private List<mx.ades.modules.horarios.config.HorarioIndisponibilidad> indisponibilidades;

    @PlanningScore
    private HardSoftScore score;
}