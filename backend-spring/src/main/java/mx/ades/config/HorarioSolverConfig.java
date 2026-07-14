package mx.ades.config;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.SolverManagerConfig;
import mx.ades.modules.horarios.application.service.HorarioSolverService;
import mx.ades.modules.horarios.HorarioCorridaRepository;
import mx.ades.modules.horarios.HorarioRepository;
import mx.ades.modules.horarios.solver.HorarioConstraintProvider;
import mx.ades.modules.horarios.solver.HorarioLeccion;
import mx.ades.modules.horarios.solver.HorarioPlan;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.score.HardSoftScore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HorarioSolverConfig {

    @Bean
    public SolverConfig timefoldSolverConfig() {
        return new SolverConfig()
                .withSolutionClass(HorarioPlan.class)
                .withEntityClasses(HorarioLeccion.class)
                .withConstraintProviderClass(HorarioConstraintProvider.class)
                .withTerminationSpentLimit(java.time.Duration.ofSeconds(30));
    }

    @Bean
    public SolverFactory<HorarioPlan> horarioSolverFactory(SolverConfig timefoldSolverConfig) {
        return SolverFactory.create(timefoldSolverConfig);
    }

    @Bean
    public SolverManager<HorarioPlan> horarioSolverManager(SolverFactory<HorarioPlan> horarioSolverFactory) {
        return SolverManager.create(horarioSolverFactory, new SolverManagerConfig());
    }

    @Bean
    public SolutionManager<HorarioPlan, HardSoftScore> horarioSolutionManager(SolverFactory<HorarioPlan> horarioSolverFactory) {
        return SolutionManager.create(horarioSolverFactory);
    }

    @Bean
    public HorarioSolverService horarioSolverService(
            SolverManager<HorarioPlan> horarioSolverManager,
            SolutionManager<HorarioPlan, HardSoftScore> horarioSolutionManager,
            HorarioCorridaRepository corridaRepository,
            HorarioRepository horarioRepository,
            mx.ades.modules.horarios.config.HorarioReglaRepository reglaRepository,
            mx.ades.modules.horarios.config.HorarioFranjaRepository franjaRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            mx.ades.modules.horarios.AsignacionDocenteRepository asignacionDocenteRepository) {
        return new HorarioSolverService(horarioSolverManager, horarioSolutionManager, corridaRepository, horarioRepository, jdbc, reglaRepository, franjaRepository, asignacionDocenteRepository);
    }
}