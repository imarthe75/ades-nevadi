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

    // ── learning_paths (FASE 18 + FASE 53) ───────────────────────────────────

    @Bean
    public LearningPathApplicationService learningPathApplicationService(LearningPathRepositoryPort repo) {
        return new LearningPathApplicationService(repo);
    }

    @Bean
    public RegistrarProgresoUseCase registrarProgreso(LearningPathApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.learning_paths.domain.port.in.CrearLearningPathUseCase crearLearningPath(
            LearningPathApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.learning_paths.domain.port.in.AsignarPathUseCase asignarPath(
            LearningPathApplicationService service) {
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

    // ── movilidad (FASE 21) ───────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.movilidad.application.service.MovilidadApplicationService movilidadApplicationService(
            mx.ades.modules.movilidad.domain.port.out.MovilidadRepositoryPort repo) {
        return new mx.ades.modules.movilidad.application.service.MovilidadApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.movilidad.domain.port.in.RegistrarCambioGrupoUseCase registrarCambioGrupo(
            mx.ades.modules.movilidad.application.service.MovilidadApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.movilidad.domain.port.in.RegistrarBajaUseCase registrarBajaMovilidad(
            mx.ades.modules.movilidad.application.service.MovilidadApplicationService service) {
        return service;
    }

    // ── justificaciones (FASE 22) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.justificaciones.application.service.JustificacionApplicationService justificacionApplicationService(
            mx.ades.modules.justificaciones.domain.port.out.JustificacionRepositoryPort repo) {
        return new mx.ades.modules.justificaciones.application.service.JustificacionApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.justificaciones.domain.port.in.RegistrarJustificacionUseCase registrarJustificacion(
            mx.ades.modules.justificaciones.application.service.JustificacionApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.justificaciones.domain.port.in.ResolverJustificacionUseCase resolverJustificacion(
            mx.ades.modules.justificaciones.application.service.JustificacionApplicationService service) {
        return service;
    }

    // ── condiciones (FASE 23) ─────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.condiciones.application.service.CondicionCronicaApplicationService condicionCronicaApplicationService(
            mx.ades.modules.condiciones.domain.port.out.CondicionRepositoryPort repo) {
        return new mx.ades.modules.condiciones.application.service.CondicionCronicaApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.condiciones.domain.port.in.RegistrarCondicionUseCase registrarCondicion(
            mx.ades.modules.condiciones.application.service.CondicionCronicaApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.condiciones.domain.port.in.ActualizarCondicionUseCase actualizarCondicion(
            mx.ades.modules.condiciones.application.service.CondicionCronicaApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.condiciones.domain.port.in.EliminarCondicionUseCase eliminarCondicion(
            mx.ades.modules.condiciones.application.service.CondicionCronicaApplicationService service) {
        return service;
    }

    // ── licencias (FASE 24) ───────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.licencias.application.service.LicenciaApplicationService licenciaApplicationService(
            mx.ades.modules.licencias.domain.port.out.LicenciaRepositoryPort repo) {
        return new mx.ades.modules.licencias.application.service.LicenciaApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.licencias.domain.port.in.SolicitarLicenciaUseCase solicitar(
            mx.ades.modules.licencias.application.service.LicenciaApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.licencias.domain.port.in.ResolverLicenciaUseCase resolverLicencia(
            mx.ades.modules.licencias.application.service.LicenciaApplicationService service) {
        return service;
    }

    // ── compliance (FASE 29) ─────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.compliance.application.service.ComplianceApplicationService complianceApplicationService(
            mx.ades.modules.compliance.domain.port.out.ComplianceRepositoryPort repo) {
        return new mx.ades.modules.compliance.application.service.ComplianceApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.compliance.domain.port.in.RegistrarNormativaUseCase registrarNormativa(
            mx.ades.modules.compliance.application.service.ComplianceApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.compliance.domain.port.in.RegistrarRetencionUseCase registrarRetencion(
            mx.ades.modules.compliance.application.service.ComplianceApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.compliance.domain.port.in.CrearAlertaUseCase crearAlerta(
            mx.ades.modules.compliance.application.service.ComplianceApplicationService service) {
        return service;
    }

    // ── comunicados (FASE 28) ─────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.comunicados.application.service.ComunicadoApplicationService comunicadoApplicationService(
            mx.ades.modules.comunicados.domain.port.out.ComunicadoRepositoryPort repo,
            mx.ades.common.PushService pushService) {
        return new mx.ades.modules.comunicados.application.service.ComunicadoApplicationService(repo, pushService);
    }

    @Bean
    public mx.ades.modules.comunicados.domain.port.in.CrearComunicadoUseCase crearComunicado(
            mx.ades.modules.comunicados.application.service.ComunicadoApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.comunicados.domain.port.in.AcusarComunicadoUseCase acusarComunicado(
            mx.ades.modules.comunicados.application.service.ComunicadoApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.comunicados.domain.port.in.ProgramarSiguienteUseCase programarSiguiente(
            mx.ades.modules.comunicados.application.service.ComunicadoApplicationService service) {
        return service;
    }

    // ── badges (FASE 27) ─────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.badges.application.service.BadgeApplicationService badgeApplicationService(
            mx.ades.modules.badges.domain.port.out.BadgeRepositoryPort repo) {
        return new mx.ades.modules.badges.application.service.BadgeApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.badges.domain.port.in.CrearBadgeUseCase crearBadge(
            mx.ades.modules.badges.application.service.BadgeApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.badges.domain.port.in.OtorgarBadgeUseCase otorgarBadge(
            mx.ades.modules.badges.application.service.BadgeApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.badges.domain.port.in.RevocarBadgeUseCase revocarBadge(
            mx.ades.modules.badges.application.service.BadgeApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.badges.domain.port.in.AutoEvaluarBadgesUseCase autoEvaluarBadges(
            mx.ades.modules.badges.application.service.BadgeApplicationService service) {
        return service;
    }

    // ── disponibilidad (FASE 26) ──────────────────────────────────────────────
    @Bean
    public mx.ades.modules.disponibilidad.application.service.DisponibilidadApplicationService disponibilidadApplicationService(
            mx.ades.modules.disponibilidad.domain.port.out.DisponibilidadRepositoryPort repo) {
        return new mx.ades.modules.disponibilidad.application.service.DisponibilidadApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.disponibilidad.domain.port.in.GuardarDisponibilidadUseCase guardarDisponibilidad(
            mx.ades.modules.disponibilidad.application.service.DisponibilidadApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.disponibilidad.domain.port.in.EliminarSlotUseCase eliminarSlot(
            mx.ades.modules.disponibilidad.application.service.DisponibilidadApplicationService service) {
        return service;
    }

    // ── notificaciones (FASE 36) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.notificaciones.application.service.NotificacionApplicationService notificacionApplicationService(
            mx.ades.modules.notificaciones.domain.port.out.NotificacionWriteRepositoryPort repo) {
        return new mx.ades.modules.notificaciones.application.service.NotificacionApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.notificaciones.domain.port.in.MarcarLeidaUseCase marcarLeidaUseCase(
            mx.ades.modules.notificaciones.application.service.NotificacionApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.notificaciones.domain.port.in.MarcarTodasLeidasUseCase marcarTodasLeidasUseCase(
            mx.ades.modules.notificaciones.application.service.NotificacionApplicationService service) {
        return service;
    }

    // ── personal_admin (FASE 35) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.personal_admin.application.service.PersonalAdminApplicationService personalAdminApplicationService(
            mx.ades.modules.personal_admin.domain.port.out.PersonalAdminRepositoryPort repo) {
        return new mx.ades.modules.personal_admin.application.service.PersonalAdminApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.personal_admin.domain.port.in.RegistrarPersonalAdminUseCase registrarPersonalAdminUseCase(
            mx.ades.modules.personal_admin.application.service.PersonalAdminApplicationService service) {
        return service;
    }

    // ── entregas (FASE 34) ────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.entregas.application.service.EntregaApplicationService entregaApplicationService(
            mx.ades.modules.entregas.domain.port.out.EntregaRepositoryPort repo) {
        return new mx.ades.modules.entregas.application.service.EntregaApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.entregas.domain.port.in.SubirEntregaUseCase subirEntregaUseCase(
            mx.ades.modules.entregas.application.service.EntregaApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.entregas.domain.port.in.CalificarEntregaUseCase calificarEntregaUseCase(
            mx.ades.modules.entregas.application.service.EntregaApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.entregas.domain.port.in.RegistrarExcusaUseCase registrarExcusaUseCase(
            mx.ades.modules.entregas.application.service.EntregaApplicationService service) {
        return service;
    }

    // ── esquemas_ponderacion (FASE 33) ───────────────────────────────────────
    @Bean
    public mx.ades.modules.esquemas_ponderacion.application.service.EsquemaApplicationService esquemaApplicationService(
            mx.ades.modules.esquemas_ponderacion.domain.port.out.EsquemaRepositoryPort repo) {
        return new mx.ades.modules.esquemas_ponderacion.application.service.EsquemaApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.esquemas_ponderacion.domain.port.in.CrearEsquemaUseCase crearEsquemaUseCase(
            mx.ades.modules.esquemas_ponderacion.application.service.EsquemaApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.esquemas_ponderacion.domain.port.in.ActualizarEsquemaUseCase actualizarEsquemaUseCase(
            mx.ades.modules.esquemas_ponderacion.application.service.EsquemaApplicationService service) {
        return service;
    }

    // ── expediente_laboral (FASE 32) ─────────────────────────────────────────
    @Bean
    public mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService expedienteLaboralApplicationService(
            mx.ades.modules.expediente_laboral.domain.port.out.ExpedienteLaboralRepositoryPort repo) {
        return new mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.expediente_laboral.domain.port.in.CrearExpedienteLaboralUseCase crearExpedienteLaboralUseCase(
            mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.expediente_laboral.domain.port.in.ActualizarExpedienteLaboralUseCase actualizarExpedienteLaboralUseCase(
            mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.expediente_laboral.domain.port.in.AgregarDocumentoLaboralUseCase agregarDocumentoLaboralUseCase(
            mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService service) {
        return service;
    }

    // ── eval_docente (FASE 31) ────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.eval_docente.application.service.EvalDocenteApplicationService evalDocenteApplicationService(
            mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort repo) {
        return new mx.ades.modules.eval_docente.application.service.EvalDocenteApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.eval_docente.domain.port.in.CrearEvaluacionUseCase crearEvaluacionUseCase(
            mx.ades.modules.eval_docente.application.service.EvalDocenteApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.eval_docente.domain.port.in.GuardarCriteriosUseCase guardarCriteriosUseCase(
            mx.ades.modules.eval_docente.application.service.EvalDocenteApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.eval_docente.domain.port.in.EnviarEvaluacionUseCase enviarEvaluacionUseCase(
            mx.ades.modules.eval_docente.application.service.EvalDocenteApplicationService service) {
        return service;
    }

    // ── asistencia personal (FASE 30) ────────────────────────────────────────
    @Bean
    public mx.ades.modules.asistencia_personal.application.service.AsistenciaPersonalApplicationService asistenciaPersonalApplicationService(
            mx.ades.modules.asistencia_personal.domain.port.out.AsistenciaPersonalRepositoryPort repo) {
        return new mx.ades.modules.asistencia_personal.application.service.AsistenciaPersonalApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.asistencia_personal.domain.port.in.RegistrarAsistenciaUseCase registrarAsistenciaUseCase(
            mx.ades.modules.asistencia_personal.application.service.AsistenciaPersonalApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.asistencia_personal.domain.port.in.ActualizarAsistenciaUseCase actualizarAsistenciaUseCase(
            mx.ades.modules.asistencia_personal.application.service.AsistenciaPersonalApplicationService service) {
        return service;
    }

    // ── capacitaciones (FASE 25) ──────────────────────────────────────────────
    @Bean
    public mx.ades.modules.capacitaciones.application.service.CapacitacionApplicationService capacitacionApplicationService(
            mx.ades.modules.capacitaciones.domain.port.out.CapacitacionRepositoryPort repo) {
        return new mx.ades.modules.capacitaciones.application.service.CapacitacionApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.capacitaciones.domain.port.in.RegistrarCapacitacionUseCase registrarCapacitacion(
            mx.ades.modules.capacitaciones.application.service.CapacitacionApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.capacitaciones.domain.port.in.ValidarCapacitacionUseCase validarCapacitacion(
            mx.ades.modules.capacitaciones.application.service.CapacitacionApplicationService service) {
        return service;
    }

    // ── personal_salud / medico (FASE 37) ────────────────────────────────────
    @Bean
    public mx.ades.modules.medico.application.service.PersonalSaludApplicationService personalSaludApplicationService(
            mx.ades.modules.medico.domain.port.out.PersonalSaludRepositoryPort repo) {
        return new mx.ades.modules.medico.application.service.PersonalSaludApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.medico.domain.port.in.RegistrarPersonalSaludUseCase registrarPersonalSaludUseCase(
            mx.ades.modules.medico.application.service.PersonalSaludApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.medico.domain.port.in.ActualizarPersonalSaludUseCase actualizarPersonalSaludUseCase(
            mx.ades.modules.medico.application.service.PersonalSaludApplicationService service) {
        return service;
    }

    // ── salud_avanzada (FASE 38) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.medico.application.service.SaludAvanzadaApplicationService saludAvanzadaApplicationService(
            mx.ades.modules.medico.domain.port.out.SaludAvanzadaRepositoryPort repo) {
        return new mx.ades.modules.medico.application.service.SaludAvanzadaApplicationService(repo);
    }

    // ── cierre_ciclo (FASE 41) ────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.cierre.application.service.CierreApplicationService cierreApplicationService(
            mx.ades.modules.cierre.domain.port.out.CierreRepositoryPort repo,
            mx.ades.modules.cierre.CierreCicloService cierreCicloService) {
        return new mx.ades.modules.cierre.application.service.CierreApplicationService(repo, cierreCicloService);
    }

    @Bean
    public mx.ades.modules.cierre.domain.port.in.CerrarCicloUseCase cerrarCicloUseCase(
            mx.ades.modules.cierre.application.service.CierreApplicationService service) {
        return service;
    }

    // ── horarios (FASE 42) ────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.horarios.application.service.HorarioApplicationService horarioApplicationService(
            mx.ades.modules.horarios.domain.port.out.HorarioWriteRepositoryPort repo) {
        return new mx.ades.modules.horarios.application.service.HorarioApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.horarios.domain.port.in.CrearHorarioUseCase crearHorarioUseCase(
            mx.ades.modules.horarios.application.service.HorarioApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.horarios.domain.port.in.ActualizarHorarioUseCase actualizarHorarioUseCase(
            mx.ades.modules.horarios.application.service.HorarioApplicationService service) {
        return service;
    }

    // ── planes_estudio (FASE 44) ──────────────────────────────────────────────
    @Bean
    public mx.ades.modules.planes_estudio.application.service.PlanEstudioApplicationService planEstudioApplicationService(
            mx.ades.modules.planes_estudio.domain.port.out.PlanEstudioRepositoryPort repo) {
        return new mx.ades.modules.planes_estudio.application.service.PlanEstudioApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.planes_estudio.domain.port.in.AsignarMateriaUseCase asignarMateriaUseCase(
            mx.ades.modules.planes_estudio.application.service.PlanEstudioApplicationService service) {
        return service;
    }

    // ── admin promocion (FASE 58) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.admin.application.service.PromocionApplicationService promocionApplicationService(
            mx.ades.modules.admin.domain.port.out.PromocionRepositoryPort repo) {
        return new mx.ades.modules.admin.application.service.PromocionApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.admin.domain.port.in.EvaluarPromocionUseCase evaluarPromocion(
            mx.ades.modules.admin.application.service.PromocionApplicationService service) {
        return service;
    }

    // ── contactos (FASE 52) ───────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.contactos.application.service.ContactosApplicationService contactosApplicationService(
            mx.ades.modules.contactos.domain.port.out.ContactosRepositoryPort repo) {
        return new mx.ades.modules.contactos.application.service.ContactosApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.contactos.domain.port.in.RegistrarContactoUseCase registrarContactoUseCase(
            mx.ades.modules.contactos.application.service.ContactosApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.contactos.domain.port.in.ActualizarContactoUseCase actualizarContactoUseCase(
            mx.ades.modules.contactos.application.service.ContactosApplicationService service) {
        return service;
    }

    // ── portal_familias (FASE 59) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.portal_familias.application.service.PortalFamiliasApplicationService portalFamiliasApplicationService(
            mx.ades.modules.portal_familias.domain.port.out.PortalFamiliasRepositoryPort repo) {
        return new mx.ades.modules.portal_familias.application.service.PortalFamiliasApplicationService(repo);
    }

    @Bean
    public mx.ades.modules.portal_familias.domain.port.in.AgregarTutorUseCase agregarTutorUseCase(
            mx.ades.modules.portal_familias.application.service.PortalFamiliasApplicationService service) {
        return service;
    }

    // ── alumnos (hexagonal) ───────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.alumnos.application.service.AlumnoApplicationService alumnoApplicationService(
            mx.ades.modules.alumnos.domain.port.out.AlumnoRepositoryPort repositoryPort,
            mx.ades.modules.admin.AdminWriteService adminWrite,
            mx.ades.shared.persona.PersonaUpdateHelper personaHelper,
            mx.ades.modules.alumnos.AlumnoComplementariosService complementariosService,
            mx.ades.modules.alumnos.query.AlumnoQueryService queryService) {
        return new mx.ades.modules.alumnos.application.service.AlumnoApplicationService(
                repositoryPort, adminWrite, personaHelper, complementariosService, queryService);
    }

    @Bean
    public mx.ades.modules.alumnos.domain.port.in.CrearAlumnoUseCase crearAlumnoUseCase(
            mx.ades.modules.alumnos.application.service.AlumnoApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.alumnos.domain.port.in.ActualizarAlumnoUseCase actualizarAlumnoUseCase(
            mx.ades.modules.alumnos.application.service.AlumnoApplicationService service) {
        return service;
    }

    // ── profesores (hexagonal) ────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.profesores.application.service.ProfesorApplicationService profesorApplicationService(
            mx.ades.modules.profesores.domain.port.out.ProfesorRepositoryPort repositoryPort,
            mx.ades.shared.persona.PersonaUpdateHelper personaHelper,
            mx.ades.modules.profesores.ProfesorLaboralesService laboralesService,
            mx.ades.modules.profesores.query.ProfesorQueryService queryService) {
        return new mx.ades.modules.profesores.application.service.ProfesorApplicationService(
                repositoryPort, personaHelper, laboralesService, queryService);
    }

    @Bean
    public mx.ades.modules.profesores.domain.port.in.CrearProfesorUseCase crearProfesorUseCase(
            mx.ades.modules.profesores.application.service.ProfesorApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.profesores.domain.port.in.ActualizarProfesorUseCase actualizarProfesorUseCase(
            mx.ades.modules.profesores.application.service.ProfesorApplicationService service) {
        return service;
    }

    // ── materias (hexagonal) ──────────────────────────────────────────────────

    @Bean
    public mx.ades.modules.materias.application.service.MateriaApplicationService materiaApplicationService(
            mx.ades.modules.materias.domain.port.out.MateriaRepositoryPort repositoryPort) {
        return new mx.ades.modules.materias.application.service.MateriaApplicationService(repositoryPort);
    }

    @Bean
    public mx.ades.modules.materias.domain.port.in.CrearMateriaUseCase crearMateriaUseCase(
            mx.ades.modules.materias.application.service.MateriaApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.materias.domain.port.in.ActualizarMateriaUseCase actualizarMateriaUseCase(
            mx.ades.modules.materias.application.service.MateriaApplicationService service) {
        return service;
    }

    // ── planteles (hexagonal) ─────────────────────────────────────────────────

    @Bean
    public mx.ades.modules.planteles.application.service.PlantelApplicationService plantelApplicationService(
            mx.ades.modules.planteles.domain.port.out.PlantelRepositoryPort repositoryPort) {
        return new mx.ades.modules.planteles.application.service.PlantelApplicationService(repositoryPort);
    }

    @Bean
    public mx.ades.modules.planteles.domain.port.in.CrearPlantelUseCase crearPlantelUseCase(
            mx.ades.modules.planteles.application.service.PlantelApplicationService service) {
        return service;
    }

    @Bean
    public mx.ades.modules.planteles.domain.port.in.ActualizarPlantelUseCase actualizarPlantelUseCase(
            mx.ades.modules.planteles.application.service.PlantelApplicationService service) {
        return service;
    }

    // ── certificados (hexagonal) ──────────────────────────────────────────────

    @Bean
    public mx.ades.modules.certificados.application.service.CertificadoApplicationService certificadoApplicationService(
            mx.ades.modules.certificados.domain.port.out.CertificadoFastApiPort fastApiPort) {
        return new mx.ades.modules.certificados.application.service.CertificadoApplicationService(fastApiPort);
    }

    @Bean
    public mx.ades.modules.certificados.domain.port.in.EmitirCertificadoUseCase emitirCertificadoUseCase(
            mx.ades.modules.certificados.application.service.CertificadoApplicationService service) {
        return service;
    }
}
