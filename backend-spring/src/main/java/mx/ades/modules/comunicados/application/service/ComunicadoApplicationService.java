package mx.ades.modules.comunicados.application.service;

import mx.ades.common.PushService;
import mx.ades.modules.comunicados.AcuseComunicado;
import mx.ades.modules.comunicados.Comunicado;
import mx.ades.modules.comunicados.domain.model.Periodicidad;
import mx.ades.modules.comunicados.domain.port.in.AcusarComunicadoUseCase;
import mx.ades.modules.comunicados.domain.port.in.CrearComunicadoUseCase;
import mx.ades.modules.comunicados.domain.port.in.ProgramarSiguienteUseCase;
import mx.ades.modules.comunicados.domain.port.out.ComunicadoRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: creación, acuse de recibo y programación recurrente de comunicados escolares.
 * Implementa {@link CrearComunicadoUseCase}, {@link AcusarComunicadoUseCase}
 * y {@link ProgramarSiguienteUseCase} coordinando el dominio de comunicados con
 * el puerto de repositorio y el servicio de notificaciones push (ntfy),
 * con soporte para comunicados urgentes, recurrentes y segmentados por grupo/nivel/plantel.
 *
 * @author ADES
 * @since 2026
 */
public class ComunicadoApplicationService
        implements CrearComunicadoUseCase, AcusarComunicadoUseCase, ProgramarSiguienteUseCase {

    private final ComunicadoRepositoryPort repo;
    private final PushService pushService;

    public ComunicadoApplicationService(ComunicadoRepositoryPort repo, PushService pushService) {
        this.repo = repo;
        this.pushService = pushService;
    }

    @Override
    public UUID crear(CrearComunicadoUseCase.Command cmd) {
        Comunicado c = new Comunicado();
        c.setTitulo(cmd.titulo());
        c.setContenido(cmd.contenido());
        c.setTipoComunicado(cmd.tipoComunicado() != null ? cmd.tipoComunicado() : "GENERAL");
        c.setPlantelId(cmd.plantelId());
        c.setNivelEducativoId(cmd.nivelEducativoId());
        c.setGrupoId(cmd.grupoId());
        c.setRequiereAcuse(cmd.requiereAcuse() != null && cmd.requiereAcuse());
        c.setFechaVencimiento(cmd.fechaVencimiento());
        c.setEsRecurrente(cmd.esRecurrente() != null && cmd.esRecurrente());
        c.setPeriodicidad(Boolean.TRUE.equals(cmd.esRecurrente()) ? cmd.periodicidad() : null);
        c.setCreadoPorId(cmd.creadoPorId());
        if (Boolean.TRUE.equals(cmd.esRecurrente()) && cmd.periodicidad() != null) {
            c.setProximoEnvio(LocalDateTime.now());
        }
        Comunicado saved = repo.save(c);
        triggerPushAsync(saved);
        return saved.getId();
    }

    @Override
    public void acusar(UUID comunicadoId, UUID usuarioId) {
        if (repo.findAcuse(comunicadoId, usuarioId).isEmpty()) {
            Comunicado c = repo.findById(comunicadoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));
            AcuseComunicado ac = new AcuseComunicado();
            ac.setComunicado(c);
            ac.setUsuarioId(usuarioId);
            ac.setFechaAcuse(LocalDateTime.now());
            ac.setIpOrigen("127.0.0.1");
            repo.saveAcuse(ac);
        }
    }

    @Override
    public LocalDateTime programarSiguiente(UUID comunicadoId) {
        Comunicado c = repo.findById(comunicadoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));
        if (c.getPeriodicidad() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comunicado no tiene periodicidad configurada");
        }
        LocalDateTime base = c.getProximoEnvio() != null ? c.getProximoEnvio() : LocalDateTime.now();
        LocalDateTime siguiente = Periodicidad.of(c.getPeriodicidad()).calcularSiguiente(base);
        c.setProximoEnvio(siguiente);
        repo.save(c);
        return siguiente;
    }

    public void eliminar(UUID id) {
        Comunicado c = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comunicado no encontrado"));
        c.setIsActive(false);
        repo.save(c);
    }

    private void triggerPushAsync(Comunicado c) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        executor.execute(() -> {
            try {
                List<UUID> recipientIds;
                if (c.getGrupoId() != null) {
                    recipientIds = repo.getRecipientsForGrupo(c.getGrupoId());
                } else if (c.getNivelEducativoId() != null) {
                    recipientIds = repo.getRecipientsForNivel(c.getNivelEducativoId());
                } else if (c.getPlantelId() != null) {
                    recipientIds = repo.getRecipientsForPlantel(c.getPlantelId());
                } else {
                    recipientIds = repo.getAllRecipients();
                }
                String prioridad = "URGENTE".equalsIgnoreCase(c.getTipoComunicado()) ? "high" : "default";
                pushService.sendBatchAsync(recipientIds,
                        "URGENTE".equalsIgnoreCase(c.getTipoComunicado()) ? "Nuevo comunicado urgente" : "Nuevo comunicado",
                        c.getTitulo(), prioridad,
                        List.of("comunicado", c.getTipoComunicado().toLowerCase()),
                        "https://ades.setag.mx/comunicados");
            } catch (Exception ignored) {}
        });
    }
}
