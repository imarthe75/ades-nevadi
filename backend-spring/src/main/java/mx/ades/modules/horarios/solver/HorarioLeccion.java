package mx.ades.modules.horarios.solver;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@PlanningEntity
@Getter
@Setter
public class HorarioLeccion {

    @PlanningId
    private UUID id;

    private UUID corridaId;
    private UUID cicloEscolarId;
    private UUID grupoId;
    private UUID materiaId;
    private UUID profesorId;
    private UUID aulaId;

    @PlanningPin
    private boolean fijado;

    @PlanningVariable(valueRangeProviderRefs = "timeslotRange", allowsUnassigned = false)
    private HorarioTimeslot timeslot;
}