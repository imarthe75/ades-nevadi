package mx.ades.modules.calificaciones.application.service;

import mx.ades.modules.calificaciones.domain.event.CalificacionCerradaEvent;
import mx.ades.modules.calificaciones.domain.model.Calificacion;
import mx.ades.modules.calificaciones.domain.port.in.CalcularCalificacionPeriodoUseCase;
import mx.ades.modules.calificaciones.domain.port.in.GuardarCalificacionManualUseCase;
import mx.ades.modules.calificaciones.domain.port.in.ObtenerBoletaUseCase;
import mx.ades.modules.calificaciones.domain.port.out.CalificacionRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sin @Service — registrado como @Bean en HexagonalConfig.
 * Orquesta cálculo, persistencia y publicación de eventos de dominio.
 */
public class CalificacionApplicationService
        implements CalcularCalificacionPeriodoUseCase,
                   GuardarCalificacionManualUseCase,
                   ObtenerBoletaUseCase {

    private final CalificacionRepositoryPort repository;
    private final ApplicationEventPublisher events;

    public CalificacionApplicationService(CalificacionRepositoryPort repository,
                                          ApplicationEventPublisher events) {
        this.repository = repository;
        this.events     = events;
    }

    @Override
    public void ejecutar(UUID estudianteId, UUID inscripcionId, UUID materiaId, UUID periodoId) {
        Calificacion resultado = repository.calcular(inscripcionId, materiaId, periodoId);
        publicarEvento(resultado);
    }

    @Override
    public Calificacion ejecutar(Calificacion calificacion) {
        Calificacion guardada = repository.guardar(calificacion);
        publicarEvento(guardada);
        return guardada;
    }

    @Override
    public List<Calificacion> ejecutar(UUID estudianteId) {
        return repository.findByEstudianteId(estudianteId);
    }

    private void publicarEvento(Calificacion c) {
        events.publishEvent(new CalificacionCerradaEvent(
                c.estudianteId(), c.materiaId(), c.grupoId(),
                c.periodoId(), c.calificacionFinal(),
                c.estatusPromocion(), Instant.now()));
    }
}
