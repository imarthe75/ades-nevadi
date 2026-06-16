package mx.ades.config;

import mx.ades.modules.asistencias.application.service.RegistrarAsistenciaMasivaService;
import mx.ades.modules.asistencias.domain.port.in.ConsultarAsistenciasPorClaseUseCase;
import mx.ades.modules.asistencias.domain.port.in.RegistrarAsistenciaMasivaUseCase;
import mx.ades.modules.asistencias.domain.port.out.AsistenciaRepositoryPort;
import mx.ades.modules.calificaciones.application.service.CalificacionApplicationService;
import mx.ades.modules.calificaciones.domain.port.in.CalcularCalificacionPeriodoUseCase;
import mx.ades.modules.calificaciones.domain.port.in.GuardarCalificacionManualUseCase;
import mx.ades.modules.calificaciones.domain.port.in.ObtenerBoletaUseCase;
import mx.ades.modules.calificaciones.domain.port.out.CalificacionRepositoryPort;
import mx.ades.modules.conducta.application.service.AplicarSancionService;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionUseCase;
import mx.ades.modules.conducta.domain.port.out.SancionRepositoryPort;
import mx.ades.modules.evaluaciones.application.service.TareaApplicationService;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarMasivoUseCase;
import mx.ades.modules.evaluaciones.domain.port.in.CrearActividadUseCase;
import mx.ades.modules.evaluaciones.domain.port.out.TareaRepositoryPort;
import mx.ades.modules.expediente.application.service.ExpedienteApplicationService;
import mx.ades.modules.expediente.domain.port.in.*;
import mx.ades.modules.expediente.domain.port.out.*;
import mx.ades.modules.evaluaciones.application.service.EvaluacionApplicationService;
import mx.ades.modules.evaluaciones.domain.port.in.AsignarAulaHoraUseCase;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarEvaluacionMasivoUseCase;
import mx.ades.modules.evaluaciones.domain.port.out.AsignacionAulaRepositoryPort;
import mx.ades.modules.evaluaciones.domain.port.out.CalificacionEvaluacionRepositoryPort;
import mx.ades.modules.conducta.application.service.ConductaApplicationService;
import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;
import mx.ades.modules.conducta.domain.port.out.PlanMejoraRepositoryPort;
import mx.ades.modules.reinscripcion.application.service.ReinscripcionApplicationService;
import mx.ades.modules.reinscripcion.domain.port.in.ProcesarAccionReinscripcionUseCase;
import mx.ades.modules.reinscripcion.domain.port.out.ReinscripcionRepositoryPort;
import mx.ades.modules.gradebook.application.service.GradebookApplicationService;
import mx.ades.modules.gradebook.domain.port.in.AplicarAjusteUseCase;
import mx.ades.modules.gradebook.domain.port.in.CerrarCalificacionUseCase;
import mx.ades.modules.gradebook.domain.port.out.CalificacionPeriodoRepositoryPort;
import mx.ades.modules.encuestas.application.service.EncuestaApplicationService;
import mx.ades.modules.encuestas.domain.port.in.ResponderEncuestaUseCase;
import mx.ades.modules.encuestas.domain.port.out.EncuestaRespuestaRepositoryPort;
import mx.ades.modules.learning_paths.application.service.LearningPathApplicationService;
import mx.ades.modules.learning_paths.domain.port.in.RegistrarProgresoUseCase;
import mx.ades.modules.learning_paths.domain.port.out.LearningPathRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registro central de beans de Application Services hexagonales.
 * Los servicios de dominio NO llevan @Service — se instancian aquí
 * para que la capa de dominio permanezca libre de dependencias de Spring.
 */
@Configuration
public class HexagonalConfig {

    // ── asistencias (FASE 1) ──────────────────────────────────────────────────

    @Bean
    public RegistrarAsistenciaMasivaUseCase registrarAsistenciaMasiva(
            AsistenciaRepositoryPort repository) {
        return new RegistrarAsistenciaMasivaService(repository);
    }

    @Bean
    public ConsultarAsistenciasPorClaseUseCase consultarAsistenciasPorClase(
            AsistenciaRepositoryPort repository) {
        return new RegistrarAsistenciaMasivaService(repository);
    }

    // ── calificaciones (FASE 2) ───────────────────────────────────────────────

    @Bean
    public CalificacionApplicationService calificacionApplicationService(
            CalificacionRepositoryPort repository,
            ApplicationEventPublisher events) {
        return new CalificacionApplicationService(repository, events);
    }

    @Bean
    public CalcularCalificacionPeriodoUseCase calcularCalificacionPeriodo(
            CalificacionApplicationService service) {
        return service;
    }

    @Bean
    public GuardarCalificacionManualUseCase guardarCalificacionManual(
            CalificacionApplicationService service) {
        return service;
    }

    @Bean
    public ObtenerBoletaUseCase obtenerBoleta(
            CalificacionApplicationService service) {
        return service;
    }

    // ── conducta (FASE 2) ─────────────────────────────────────────────────────

    @Bean
    public AplicarSancionUseCase aplicarSancion(
            SancionRepositoryPort sancionRepository,
            ApplicationEventPublisher events) {
        return new AplicarSancionService(sancionRepository, events);
    }

    // ── evaluaciones/tareas (FASE 2B) ─────────────────────────────────────────

    @Bean
    public TareaApplicationService tareaApplicationService(
            TareaRepositoryPort tareaRepository,
            ApplicationEventPublisher events) {
        return new TareaApplicationService(tareaRepository, events);
    }

    @Bean
    public CrearActividadUseCase crearActividad(TareaApplicationService service) {
        return service;
    }

    @Bean
    public CalificarMasivoUseCase calificarMasivo(TareaApplicationService service) {
        return service;
    }

    // ── gradebook (FASE 3) ────────────────────────────────────────────────────

    @Bean
    public GradebookApplicationService gradebookApplicationService(
            CalificacionPeriodoRepositoryPort calificacionRepository,
            ApplicationEventPublisher events) {
        return new GradebookApplicationService(calificacionRepository, events);
    }

    @Bean
    public AplicarAjusteUseCase aplicarAjuste(GradebookApplicationService service) {
        return service;
    }

    @Bean
    public CerrarCalificacionUseCase cerrarCalificacion(GradebookApplicationService service) {
        return service;
    }

    // ── expediente (FASE 5) ───────────────────────────────────────────────────

    @Bean
    public ExpedienteApplicationService expedienteApplicationService(
            BajaRepositoryPort bajaRepo,
            ExtraordinarioRepositoryPort extraRepo,
            ConstanciaRepositoryPort constanciaRepo,
            ExpedienteRepositoryPort expedienteRepo,
            ApplicationEventPublisher events) {
        return new ExpedienteApplicationService(bajaRepo, extraRepo, constanciaRepo, expedienteRepo, events);
    }

    @Bean
    public RegistrarBajaUseCase registrarBaja(ExpedienteApplicationService service) {
        return service;
    }

    @Bean
    public CalificarExtraordinarioUseCase calificarExtraordinario(ExpedienteApplicationService service) {
        return service;
    }

    @Bean
    public EmitirConstanciaUseCase emitirConstancia(ExpedienteApplicationService service) {
        return service;
    }

    @Bean
    public VerificarExpedienteUseCase verificarExpediente(ExpedienteApplicationService service) {
        return service;
    }

    // ── reinscripcion (FASE 6) ────────────────────────────────────────────────

    @Bean
    public ReinscripcionApplicationService reinscripcionApplicationService(
            ReinscripcionRepositoryPort repo,
            ApplicationEventPublisher events) {
        return new ReinscripcionApplicationService(repo, events);
    }

    @Bean
    public ProcesarAccionReinscripcionUseCase procesarAccionReinscripcion(
            ReinscripcionApplicationService service) {
        return service;
    }

    // ── conducta (FASE 12) ────────────────────────────────────────────────────

    @Bean
    public ConductaApplicationService conductaApplicationService(PlanMejoraRepositoryPort planRepo) {
        return new ConductaApplicationService(planRepo);
    }

    @Bean
    public CrearPlanMejoraUseCase crearPlanMejora(ConductaApplicationService service) {
        return service;
    }

    // ── evaluaciones TIER 2 (FASE 9-11) ──────────────────────────────────────

    @Bean
    public EvaluacionApplicationService evaluacionApplicationService(
            AsignacionAulaRepositoryPort aulaRepo,
            CalificacionEvaluacionRepositoryPort calificacionRepo,
            ApplicationEventPublisher events) {
        return new EvaluacionApplicationService(aulaRepo, calificacionRepo, events);
    }

    @Bean
    public AsignarAulaHoraUseCase asignarAulaHora(EvaluacionApplicationService service) {
        return service;
    }

    @Bean
    public CalificarEvaluacionMasivoUseCase calificarEvaluacionMasivo(EvaluacionApplicationService service) {
        return service;
    }

    // ── encuestas (FASE 17) ───────────────────────────────────────────────────

    @Bean
    public EncuestaApplicationService encuestaApplicationService(EncuestaRespuestaRepositoryPort repo) {
        return new EncuestaApplicationService(repo);
    }

    @Bean
    public ResponderEncuestaUseCase responderEncuesta(EncuestaApplicationService service) {
        return service;
    }

    // ── learning_paths (FASE 18) ──────────────────────────────────────────────

    @Bean
    public LearningPathApplicationService learningPathApplicationService(LearningPathRepositoryPort repo) {
        return new LearningPathApplicationService(repo);
    }

    @Bean
    public RegistrarProgresoUseCase registrarProgreso(LearningPathApplicationService service) {
        return service;
    }

    // ── procesos (FASE 16) ────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.procesos.application.service.ProcesosApplicationService procesosApplicationService(
            mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort repo) {
        return new mx.ades.modules.procesos.application.service.ProcesosApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.procesos.domain.port.in.ProcesarPreinscripcionUseCase procesarPreinscripcion(
            mx.ades.modules.procesos.application.service.ProcesosApplicationService service) {
        return service;
    }
}
