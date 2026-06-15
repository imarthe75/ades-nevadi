package mx.ades.modules.evaluaciones.application.service;

import mx.ades.modules.evaluaciones.domain.event.ActividadCreadaEvent;
import mx.ades.modules.evaluaciones.domain.model.ItemCalificacion;
import mx.ades.modules.evaluaciones.domain.port.in.CalificarMasivoUseCase;
import mx.ades.modules.evaluaciones.domain.port.in.CrearActividadUseCase;
import mx.ades.modules.evaluaciones.domain.port.out.TareaRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service del módulo evaluaciones — Tareas.
 * No lleva @Service: se registra manualmente en HexagonalConfig.
 */
public class TareaApplicationService implements CrearActividadUseCase, CalificarMasivoUseCase {

    private final TareaRepositoryPort repository;
    private final ApplicationEventPublisher events;

    public TareaApplicationService(TareaRepositoryPort repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events     = events;
    }

    @Override
    public CrearActividadUseCase.Result ejecutar(CrearActividadUseCase.Command cmd) {
        TareaRepositoryPort.TareaDatos datos = new TareaRepositoryPort.TareaDatos(
                cmd.titulo(), cmd.descripcion(),
                cmd.grupoId(), cmd.materiaId(), cmd.temaId(),
                cmd.periodoEvaluacionId(),
                cmd.fechaAsignacion(), cmd.fechaEntrega(),
                cmd.puntajeMaximo() != null ? cmd.puntajeMaximo() : BigDecimal.TEN,
                cmd.tipoItem(), cmd.permiteEntregaTarde(),
                cmd.instruccionesUrl(), cmd.creadorUsername());

        UUID tareaId = repository.guardar(datos);

        List<UUID> estudiantes = repository.findEstudiantesEnGrupo(cmd.grupoId());
        int slots = repository.crearSlots(tareaId, estudiantes);

        events.publishEvent(new ActividadCreadaEvent(
                tareaId, cmd.grupoId(), cmd.materiaId(), cmd.tipoItem(), slots, Instant.now()));

        return new CrearActividadUseCase.Result(tareaId, slots);
    }

    @Override
    public int ejecutar(CalificarMasivoUseCase.Command cmd) {
        BigDecimal puntajeMaximo = repository.findPuntajeMaximo(cmd.tareaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actividad no encontrada"));

        List<ItemCalificacion> invalidas = cmd.items().stream()
                .filter(item -> item.excedePuntajeMaximo(puntajeMaximo))
                .toList();

        if (!invalidas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Calificación excede el puntaje máximo de " + puntajeMaximo +
                    " para " + invalidas.size() + " ítem(s)");
        }

        return repository.calificarItems(cmd.tareaId(), cmd.items(), cmd.calificadorId());
    }
}
