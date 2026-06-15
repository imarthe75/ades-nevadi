package mx.ades.modules.conducta.application.service;

import mx.ades.modules.conducta.domain.event.SancionConductaEvent;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionCommand;
import mx.ades.modules.conducta.domain.port.in.AplicarSancionUseCase;
import mx.ades.modules.conducta.domain.port.out.SancionRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Sin @Service — registrado como @Bean en HexagonalConfig.
 * Regla de negocio: solo nivelAcceso <= 2 (DIRECTOR/ADMIN) puede aplicar sanciones formales.
 */
public class AplicarSancionService implements AplicarSancionUseCase {

    private final SancionRepositoryPort sancionRepository;
    private final ApplicationEventPublisher events;

    public AplicarSancionService(SancionRepositoryPort sancionRepository,
                                 ApplicationEventPublisher events) {
        this.sancionRepository = sancionRepository;
        this.events            = events;
    }

    @Override
    public UUID ejecutar(AplicarSancionCommand cmd) {
        if (cmd.nivelAccesoAplicador() > 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo DIRECTOR/ADMIN puede aplicar sanciones formales");
        }

        // obtener estudianteId del reporte (el adapter resuelve esta dependencia)
        UUID sancionId = sancionRepository.guardar(cmd.reporteId(), cmd);

        events.publishEvent(new SancionConductaEvent(
                sancionId, cmd.reporteId(), cmd.reporteId(),
                cmd.tipoSancion(), cmd.notificadoPadres(), Instant.now()));

        return sancionId;
    }
}
