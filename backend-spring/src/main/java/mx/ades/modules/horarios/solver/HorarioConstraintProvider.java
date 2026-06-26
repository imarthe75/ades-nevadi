package mx.ades.modules.horarios.solver;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

public class HorarioConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                profesorEnDosClasesAlMismoTiempo(factory),
                grupoEnDosClasesAlMismoTiempo(factory),
                aulaEnDosClasesAlMismoTiempo(factory)
        };
    }

    private Constraint profesorEnDosClasesAlMismoTiempo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getProfesorId() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getProfesorId),
                        Joiners.equal(HorarioLeccion::getTimeslot),
                        Joiners.lessThan(HorarioLeccion::getId))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Profesor en dos clases al mismo tiempo");
    }

    private Constraint grupoEnDosClasesAlMismoTiempo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getGrupoId() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getGrupoId),
                        Joiners.equal(HorarioLeccion::getTimeslot),
                        Joiners.lessThan(HorarioLeccion::getId))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Grupo en dos clases al mismo tiempo");
    }

    private Constraint aulaEnDosClasesAlMismoTiempo(ConstraintFactory factory) {
        return factory.forEach(HorarioLeccion.class)
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getAulaId() != null)
                .join(HorarioLeccion.class,
                        Joiners.equal(HorarioLeccion::getAulaId),
                        Joiners.equal(HorarioLeccion::getTimeslot),
                        Joiners.lessThan(HorarioLeccion::getId))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Aula en dos clases al mismo tiempo");
    }
}