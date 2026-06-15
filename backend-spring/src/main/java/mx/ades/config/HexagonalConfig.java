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
}
