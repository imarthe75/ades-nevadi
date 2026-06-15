package mx.ades.modules.gradebook.application.service;

import mx.ades.modules.gradebook.domain.event.AjusteAplicadoEvent;
import mx.ades.modules.gradebook.domain.event.CalificacionCerradaEvent;
import mx.ades.modules.gradebook.domain.model.CalificacionEstado;
import mx.ades.modules.gradebook.domain.port.in.AplicarAjusteUseCase;
import mx.ades.modules.gradebook.domain.port.in.CerrarCalificacionUseCase;
import mx.ades.modules.gradebook.domain.port.out.CalificacionPeriodoRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

/** No @Service — registrado manualmente en HexagonalConfig. */
public class GradebookApplicationService implements AplicarAjusteUseCase, CerrarCalificacionUseCase {

    private final CalificacionPeriodoRepositoryPort repository;
    private final ApplicationEventPublisher events;

    public GradebookApplicationService(CalificacionPeriodoRepositoryPort repository,
                                       ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Override
    public AplicarAjusteUseCase.Result ejecutar(AplicarAjusteUseCase.Command cmd) {
        CalificacionEstado estado = repository.findEstado(cmd.calPeriodoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Registro de calificación no encontrado"));

        if (!estado.permiteAjuste(cmd.esAdmin()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Calificación cerrada. Solo ADMIN puede modificarla.");

        BigDecimal calFinal = cmd.ajuste().calcularFinal(estado.calificacionCalculada());
        repository.aplicarAjuste(cmd.calPeriodoId(), cmd.ajuste(), calFinal, cmd.username());

        events.publishEvent(new AjusteAplicadoEvent(
                cmd.calPeriodoId(), cmd.ajuste().valor(), calFinal, cmd.username(), Instant.now()));

        return new AplicarAjusteUseCase.Result(calFinal);
    }

    @Override
    public void ejecutar(CerrarCalificacionUseCase.Command cmd) {
        if (!cmd.tienePermiso())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sin permiso para cerrar períodos");

        boolean cerrado = repository.cerrar(cmd.calPeriodoId(), cmd.username());
        if (!cerrado)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Calificación ya cerrada o no encontrada");

        events.publishEvent(new CalificacionCerradaEvent(
                cmd.calPeriodoId(), cmd.username(), Instant.now()));
    }
}
