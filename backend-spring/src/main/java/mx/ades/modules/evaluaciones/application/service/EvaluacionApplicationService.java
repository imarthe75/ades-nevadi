package mx.ades.modules.evaluaciones.application.service;

import mx.ades.modules.evaluaciones.domain.event.AulaAsignadaEvent;
import mx.ades.modules.evaluaciones.domain.model.SlotHorario;
import mx.ades.modules.evaluaciones.domain.port.in.AsignarAulaHoraUseCase;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarEvaluacionMasivoUseCase;
import mx.ades.modules.evaluaciones.domain.port.out.AsignacionAulaRepositoryPort;
import mx.ades.modules.evaluaciones.domain.port.out.CalificacionEvaluacionRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Caso de uso: asignación de aula/hora a exámenes y calificación masiva de evaluaciones.
 * Implementa {@link AsignarAulaHoraUseCase} y {@link CalificarEvaluacionMasivoUseCase}
 * coordinando el dominio de evaluaciones con los puertos de repositorio de aula
 * y calificación, publicando eventos de dominio para notificaciones downstream.
 * Aplica a los tres niveles educativos: Primaria NEM, Secundaria NEM y Preparatoria UAEMEX.
 *
 * @author ADES
 * @since 2026
 */
public class EvaluacionApplicationService
        implements AsignarAulaHoraUseCase, CalificarEvaluacionMasivoUseCase {

    private final AsignacionAulaRepositoryPort aulaRepo;
    private final CalificacionEvaluacionRepositoryPort calificacionRepo;
    private final ApplicationEventPublisher events;

    public EvaluacionApplicationService(AsignacionAulaRepositoryPort aulaRepo,
                                        CalificacionEvaluacionRepositoryPort calificacionRepo,
                                        ApplicationEventPublisher events) {
        this.aulaRepo = aulaRepo;
        this.calificacionRepo = calificacionRepo;
        this.events = events;
    }

    @Override
    public java.util.UUID ejecutar(AsignarAulaHoraUseCase.Command cmd) {
        if (aulaRepo.existeConflicto(cmd.aulaId(), cmd.fecha(), cmd.slot())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El aula ya está ocupada en ese horario");
        }

        java.util.UUID id = aulaRepo.guardar(
                cmd.claseId(), cmd.aulaId(), cmd.fecha(), cmd.slot(),
                cmd.observaciones(), cmd.username());

        events.publishEvent(new AulaAsignadaEvent(
                id, cmd.aulaId(), cmd.fecha(), cmd.slot(),
                cmd.username(), Instant.now()));

        return id;
    }

    @Override
    public int ejecutar(CalificarEvaluacionMasivoUseCase.Command cmd) {
        int updated = 0;
        for (CalificarEvaluacionMasivoUseCase.EntradaCalificacion entrada : cmd.calificaciones()) {
            var existente = calificacionRepo.findIdActiva(cmd.evaluacionId(), entrada.estudianteId());
            if (existente.isEmpty()) {
                calificacionRepo.insertar(cmd.evaluacionId(), entrada.estudianteId(),
                        entrada.calificacion(), entrada.comentarios(), cmd.username());
            } else {
                calificacionRepo.actualizar(existente.get(), entrada.calificacion(),
                        entrada.comentarios(), cmd.username());
            }
            updated++;
        }
        return updated;
    }
}
