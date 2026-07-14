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

    // NOTA: NO definir aquí un @Bean ObjectMapper propio — un bean manual (ej. `new
    // ObjectMapper()`) hace que Spring Boot desactive su autoconfiguración de Jackson,
    // incluyendo `spring.jackson.property-naming-strategy: SNAKE_CASE` (application.yml)
    // y el registro automático de JavaTimeModule. Eso rompió silenciosamente ~28 flujos
    // de creación/edición en todo el sistema (payloads snake_case de Angular deserializados
    // contra records/DTOs camelCase sin @JsonProperty, campos quedando null). Dejar que
    // Spring Boot autoconfigure el ObjectMapper vía application.yml.

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

    // ── gradebook (FASE 3) ────────────────────────────────────────────────────

    @Bean
    public GradebookApplicationService gradebookApplicationService(
            CalificacionPeriodoRepositoryPort calificacionRepository,
            ApplicationEventPublisher events) {
        return new GradebookApplicationService(calificacionRepository, events);
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

    // ── reinscripcion (FASE 6) ────────────────────────────────────────────────

    @Bean
    public ReinscripcionApplicationService reinscripcionApplicationService(
            ReinscripcionRepositoryPort repo,
            ApplicationEventPublisher events) {
        return new ReinscripcionApplicationService(repo, events);
    }

    // ── conducta (FASE 12) ────────────────────────────────────────────────────

    @Bean
    public ConductaApplicationService conductaApplicationService(PlanMejoraRepositoryPort planRepo) {
        return new ConductaApplicationService(planRepo);
    }

    // ── evaluaciones TIER 2 (FASE 9-11) ──────────────────────────────────────

    @Bean
    public EvaluacionApplicationService evaluacionApplicationService(
            AsignacionAulaRepositoryPort aulaRepo,
            CalificacionEvaluacionRepositoryPort calificacionRepo,
            ApplicationEventPublisher events) {
        return new EvaluacionApplicationService(aulaRepo, calificacionRepo, events);
    }

    // ── encuestas (FASE 17) ───────────────────────────────────────────────────

    @Bean
    public EncuestaApplicationService encuestaApplicationService(EncuestaRespuestaRepositoryPort repo) {
        return new EncuestaApplicationService(repo);
    }

    // ── learning_paths (FASE 18 + FASE 53) ───────────────────────────────────

    @Bean
    public LearningPathApplicationService learningPathApplicationService(LearningPathRepositoryPort repo) {
        return new LearningPathApplicationService(repo);
    }

    // ── procesos (FASE 16) ────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.procesos.application.service.ProcesosApplicationService procesosApplicationService(
            mx.ades.modules.procesos.domain.port.out.PreinscripcionRepositoryPort repo) {
        return new mx.ades.modules.procesos.application.service.ProcesosApplicationService(repo);
    }

    // ── movilidad (FASE 21) ───────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.movilidad.application.service.MovilidadApplicationService movilidadApplicationService(
            mx.ades.modules.movilidad.domain.port.out.MovilidadRepositoryPort repo) {
        return new mx.ades.modules.movilidad.application.service.MovilidadApplicationService(repo);
    }

    // ── justificaciones (FASE 22) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.justificaciones.application.service.JustificacionApplicationService justificacionApplicationService(
            mx.ades.modules.justificaciones.domain.port.out.JustificacionRepositoryPort repo) {
        return new mx.ades.modules.justificaciones.application.service.JustificacionApplicationService(repo);
    }

    // ── condiciones (FASE 23) ─────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.condiciones.application.service.CondicionCronicaApplicationService condicionCronicaApplicationService(
            mx.ades.modules.condiciones.domain.port.out.CondicionRepositoryPort repo) {
        return new mx.ades.modules.condiciones.application.service.CondicionCronicaApplicationService(repo);
    }

    // ── biblioteca ─────────────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.biblioteca.application.service.BibliotecaApplicationService bibliotecaApplicationService(
            mx.ades.modules.biblioteca.domain.port.out.BibliotecaRepositoryPort repo) {
        return new mx.ades.modules.biblioteca.application.service.BibliotecaApplicationService(repo);
    }

    // ── licencias (FASE 24) ───────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.licencias.application.service.LicenciaApplicationService licenciaApplicationService(
            mx.ades.modules.licencias.domain.port.out.LicenciaRepositoryPort repo) {
        return new mx.ades.modules.licencias.application.service.LicenciaApplicationService(repo);
    }

    // ── compliance (FASE 29) ─────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.compliance.application.service.ComplianceApplicationService complianceApplicationService(
            mx.ades.modules.compliance.domain.port.out.ComplianceRepositoryPort repo) {
        return new mx.ades.modules.compliance.application.service.ComplianceApplicationService(repo);
    }

    // ── comunicados (FASE 28) ─────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.comunicados.application.service.ComunicadoApplicationService comunicadoApplicationService(
            mx.ades.modules.comunicados.domain.port.out.ComunicadoRepositoryPort repo,
            mx.ades.common.PushService pushService) {
        return new mx.ades.modules.comunicados.application.service.ComunicadoApplicationService(repo, pushService);
    }

    // ── badges (FASE 27) ─────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.badges.application.service.BadgeApplicationService badgeApplicationService(
            mx.ades.modules.badges.domain.port.out.BadgeRepositoryPort repo) {
        return new mx.ades.modules.badges.application.service.BadgeApplicationService(repo);
    }

    // ── disponibilidad (FASE 26) ──────────────────────────────────────────────
    @Bean
    public mx.ades.modules.disponibilidad.application.service.DisponibilidadApplicationService disponibilidadApplicationService(
            mx.ades.modules.disponibilidad.domain.port.out.DisponibilidadRepositoryPort repo) {
        return new mx.ades.modules.disponibilidad.application.service.DisponibilidadApplicationService(repo);
    }

    // ── notificaciones (FASE 36) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.notificaciones.application.service.NotificacionApplicationService notificacionApplicationService(
            mx.ades.modules.notificaciones.domain.port.out.NotificacionWriteRepositoryPort repo) {
        return new mx.ades.modules.notificaciones.application.service.NotificacionApplicationService(repo);
    }

    // ── personal_admin (FASE 35) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.personal_admin.application.service.PersonalAdminApplicationService personalAdminApplicationService(
            mx.ades.modules.personal_admin.domain.port.out.PersonalAdminRepositoryPort repo) {
        return new mx.ades.modules.personal_admin.application.service.PersonalAdminApplicationService(repo);
    }

    // ── entregas (FASE 34) ────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.entregas.application.service.EntregaApplicationService entregaApplicationService(
            mx.ades.modules.entregas.domain.port.out.EntregaRepositoryPort repo) {
        return new mx.ades.modules.entregas.application.service.EntregaApplicationService(repo);
    }

    // ── esquemas_ponderacion (FASE 33) ───────────────────────────────────────
    @Bean
    public mx.ades.modules.esquemas_ponderacion.application.service.EsquemaApplicationService esquemaApplicationService(
            mx.ades.modules.esquemas_ponderacion.domain.port.out.EsquemaRepositoryPort repo) {
        return new mx.ades.modules.esquemas_ponderacion.application.service.EsquemaApplicationService(repo);
    }

    // ── expediente_laboral (FASE 32) ─────────────────────────────────────────
    @Bean
    public mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService expedienteLaboralApplicationService(
            mx.ades.modules.expediente_laboral.domain.port.out.ExpedienteLaboralRepositoryPort repo) {
        return new mx.ades.modules.expediente_laboral.application.service.ExpedienteLaboralApplicationService(repo);
    }

    // ── eval_docente (FASE 31) ────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.eval_docente.application.service.EvalDocenteApplicationService evalDocenteApplicationService(
            mx.ades.modules.eval_docente.domain.port.out.EvalDocenteRepositoryPort repo) {
        return new mx.ades.modules.eval_docente.application.service.EvalDocenteApplicationService(repo);
    }

    // ── asistencia personal (FASE 30) ────────────────────────────────────────
    @Bean
    public mx.ades.modules.asistencia_personal.application.service.AsistenciaPersonalApplicationService asistenciaPersonalApplicationService(
            mx.ades.modules.asistencia_personal.domain.port.out.AsistenciaPersonalRepositoryPort repo) {
        return new mx.ades.modules.asistencia_personal.application.service.AsistenciaPersonalApplicationService(repo);
    }

    // ── capacitaciones (FASE 25) ──────────────────────────────────────────────
    @Bean
    public mx.ades.modules.capacitaciones.application.service.CapacitacionApplicationService capacitacionApplicationService(
            mx.ades.modules.capacitaciones.domain.port.out.CapacitacionRepositoryPort repo) {
        return new mx.ades.modules.capacitaciones.application.service.CapacitacionApplicationService(repo);
    }

    // ── personal_salud / medico (FASE 37) ────────────────────────────────────
    @Bean
    public mx.ades.modules.medico.application.service.PersonalSaludApplicationService personalSaludApplicationService(
            mx.ades.modules.medico.domain.port.out.PersonalSaludRepositoryPort repo) {
        return new mx.ades.modules.medico.application.service.PersonalSaludApplicationService(repo);
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

    // ── horarios (FASE 42) ────────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.horarios.application.service.HorarioApplicationService horarioApplicationService(
            mx.ades.modules.horarios.domain.port.out.HorarioWriteRepositoryPort repo) {
        return new mx.ades.modules.horarios.application.service.HorarioApplicationService(repo);
    }

    // ── planes_estudio (FASE 44) ──────────────────────────────────────────────
    @Bean
    public mx.ades.modules.planes_estudio.application.service.PlanEstudioApplicationService planEstudioApplicationService(
            mx.ades.modules.planes_estudio.domain.port.out.PlanEstudioRepositoryPort repo) {
        return new mx.ades.modules.planes_estudio.application.service.PlanEstudioApplicationService(repo);
    }

    // ── admin promocion (FASE 58) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.admin.application.service.PromocionApplicationService promocionApplicationService(
            mx.ades.modules.admin.domain.port.out.PromocionRepositoryPort repo) {
        return new mx.ades.modules.admin.application.service.PromocionApplicationService(repo);
    }

    // ── contactos (FASE 52) ───────────────────────────────────────────────────
    @Bean
    public mx.ades.modules.contactos.application.service.ContactosApplicationService contactosApplicationService(
            mx.ades.modules.contactos.domain.port.out.ContactosRepositoryPort repo) {
        return new mx.ades.modules.contactos.application.service.ContactosApplicationService(repo);
    }

    // ── portal_familias (FASE 59) ─────────────────────────────────────────────
    @Bean
    public mx.ades.modules.portal_familias.application.service.PortalFamiliasApplicationService portalFamiliasApplicationService(
            mx.ades.modules.portal_familias.domain.port.out.PortalFamiliasRepositoryPort repo) {
        return new mx.ades.modules.portal_familias.application.service.PortalFamiliasApplicationService(repo);
    }

    // alumnos, profesores, materias, planteles, certificados, aulas, boletas, foros:
    // sus *ApplicationService llevan @Service — Spring los auto-descubre y registra
    // como implementadores de sus interfaces de use case sin ambigüedad de tipo.
}
