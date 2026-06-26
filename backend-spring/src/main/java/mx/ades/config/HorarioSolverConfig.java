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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HorarioSolverConfig {

    @Bean
    public SolverConfig timefoldSolverConfig() {
        return new SolverConfig()
                .withSolutionClass(HorarioPlan.class)
                .withEntityClasses(HorarioLeccion.class)
                .withConstraintProviderClass(HorarioConstraintProvider.class);
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
    public HorarioSolverService horarioSolverService(SolverManager<HorarioPlan> horarioSolverManager,
            HorarioCorridaRepository corridaRepository, HorarioRepository horarioRepository) {
        return new HorarioSolverService(horarioSolverManager, corridaRepository, horarioRepository);
    }
}