package mx.ades.modules.expediente.application.service;

import mx.ades.modules.expediente.domain.event.BajaRegistradaEvent;
import mx.ades.modules.expediente.domain.event.ExtraordinarioCalificadoEvent;
import mx.ades.modules.expediente.domain.model.TipoBaja;
import mx.ades.modules.expediente.domain.port.in.CalificarExtraordinarioUseCase;
import mx.ades.modules.expediente.domain.port.in.EmitirConstanciaUseCase;
import mx.ades.modules.expediente.domain.port.in.RegistrarBajaUseCase;
import mx.ades.modules.expediente.domain.port.in.VerificarExpedienteUseCase;
import mx.ades.modules.expediente.domain.port.out.BajaRepositoryPort;
import mx.ades.modules.expediente.domain.port.out.ConstanciaRepositoryPort;
import mx.ades.modules.expediente.domain.port.out.ExtraordinarioRepositoryPort;
import mx.ades.modules.expediente.domain.port.out.ExpedienteRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Caso de uso: gestión integral del expediente escolar del alumno.
 * Implementa {@link RegistrarBajaUseCase}, {@link CalificarExtraordinarioUseCase},
 * {@link EmitirConstanciaUseCase} y {@link VerificarExpedienteUseCase} coordinando
 * el dominio de expediente con los puertos de repositorio de bajas, exámenes
 * extraordinarios, constancias y verificación documental. Integra con Paperless-ngx
 * OCR para digitalización de documentos escaneados y publica eventos de dominio.
 *
 * @author ADES
 * @since 2026
 */
public class ExpedienteApplicationService
        implements RegistrarBajaUseCase, CalificarExtraordinarioUseCase,
                   EmitirConstanciaUseCase, VerificarExpedienteUseCase {

    private final BajaRepositoryPort bajaRepo;
    private final ExtraordinarioRepositoryPort extraRepo;
    private final ConstanciaRepositoryPort constanciaRepo;
    private final ExpedienteRepositoryPort expedienteRepo;
    private final ApplicationEventPublisher eventPublisher;

    public ExpedienteApplicationService(BajaRepositoryPort bajaRepo,
                                        ExtraordinarioRepositoryPort extraRepo,
                                        ConstanciaRepositoryPort constanciaRepo,
                                        ExpedienteRepositoryPort expedienteRepo,
                                        ApplicationEventPublisher eventPublisher) {
        this.bajaRepo = bajaRepo;
        this.extraRepo = extraRepo;
        this.constanciaRepo = constanciaRepo;
        this.expedienteRepo = expedienteRepo;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RegistrarBajaUseCase.Result ejecutar(RegistrarBajaUseCase.Command cmd) {
        TipoBaja tipo = cmd.tipo();
        UUID bajaId = bajaRepo.guardar(
                cmd.estudianteId(), tipo, cmd.motivo(),
                cmd.fechaEfectiva(), cmd.fechaReingreso(),
                cmd.plantelDestino(), cmd.claveCtDestino(),
                cmd.observaciones(), cmd.autorizadoPorId());

        boolean desactivado = tipo.desactivaEstudiante();
        if (desactivado) {
            bajaRepo.desactivarEstudiante(cmd.estudianteId());
        }

        eventPublisher.publishEvent(new BajaRegistradaEvent(
                bajaId, cmd.estudianteId(), tipo,
                cmd.fechaEfectiva(), desactivado,
                cmd.username(), Instant.now()));

        return new RegistrarBajaUseCase.Result(bajaId, desactivado);
    }

    @Override
    public void ejecutar(CalificarExtraordinarioUseCase.Command cmd) {
        if (!extraRepo.existeActivo(cmd.extraordinarioId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Extraordinario no encontrado o inactivo");
        }

        extraRepo.calificar(
                cmd.extraordinarioId(),
                cmd.calificacion().valor(),
                cmd.acredita(),
                cmd.fechaExamen());

        eventPublisher.publishEvent(new ExtraordinarioCalificadoEvent(
                cmd.extraordinarioId(), null,
                cmd.calificacion().valor(),
                cmd.acredita(),
                cmd.username(), Instant.now()));
    }

    @Override
    public EmitirConstanciaUseCase.Result ejecutar(EmitirConstanciaUseCase.Command cmd) {
        String folio = constanciaRepo.generarFolio(cmd.tipoConstancia());
        UUID id = constanciaRepo.guardar(
                cmd.estudianteId(), cmd.tipoConstancia(), folio,
                cmd.cicloEscolarId(), cmd.solicitadaPor(), cmd.proposito(),
                cmd.fechaVencimiento(), cmd.emitidaPorId());
        return new EmitirConstanciaUseCase.Result(id, folio);
    }

    @Override
    public void ejecutar(VerificarExpedienteUseCase.Command cmd) {
        expedienteRepo.marcarVerificado(
                cmd.expedienteId(), cmd.observaciones(), cmd.verificadoPorId());
    }
}
