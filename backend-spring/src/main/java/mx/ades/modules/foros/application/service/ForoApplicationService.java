package mx.ades.modules.foros.application.service;

import org.springframework.stereotype.Service;
import mx.ades.modules.foros.Anuncio;
import mx.ades.modules.foros.Foro;
import mx.ades.modules.foros.MensajeForo;
import mx.ades.modules.foros.RespuestaForo;
import mx.ades.modules.foros.domain.port.in.CrearForoUseCase;
import mx.ades.modules.foros.domain.port.in.ModerarMensajeUseCase;
import mx.ades.modules.foros.domain.port.in.PublicarAnuncioUseCase;
import mx.ades.modules.foros.domain.port.in.PublicarMensajeUseCase;
import mx.ades.modules.foros.domain.port.out.ForoRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso: gestión de foros académicos, mensajería y tablero de anuncios.
 * Implementa {@link CrearForoUseCase}, {@link PublicarMensajeUseCase},
 * {@link ModerarMensajeUseCase} y {@link PublicarAnuncioUseCase} coordinando
 * el dominio de foros con el puerto de repositorio, con soporte de moderación
 * por rol y publicación de anuncios urgentes por plantel/nivel educativo.
 *
 * @author ADES
 * @since 2026
 */
@Service
public class ForoApplicationService
        implements CrearForoUseCase, PublicarMensajeUseCase, ModerarMensajeUseCase, PublicarAnuncioUseCase {

    private final ForoRepositoryPort port;

    public ForoApplicationService(ForoRepositoryPort port) {
        this.port = port;
    }

    @Override
    public Map<String, Object> crear(CrearForoUseCase.Command cmd) {
        Foro f = new Foro();
        f.setNombre(cmd.nombre());
        f.setDescripcion(cmd.descripcion());
        f.setTipo(cmd.tipo() != null ? cmd.tipo() : "GRUPO");
        f.setGrupoId(cmd.grupoId());
        f.setPlantelId(cmd.plantelId());
        f.setMateriaId(cmd.materiaId());
        f.setEsModerado(Boolean.TRUE.equals(cmd.esModerado()));
        f.setCreadoPor(cmd.creadoPor());
        Foro saved = port.saveForo(f);
        return Map.of("id", saved.getId(), "message", "Foro creado");
    }

    @Override
    public Map<String, Object> publicar(PublicarMensajeUseCase.Command cmd) {
        Foro foro = port.findForoById(cmd.foroId())
                .filter(Foro::getIsActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Foro no encontrado"));
        String estado = Boolean.TRUE.equals(foro.getEsModerado()) ? "PENDIENTE" : "PUBLICADO";
        MensajeForo m = new MensajeForo();
        m.setForoId(cmd.foroId());
        m.setAsunto(cmd.asunto());
        m.setContenido(cmd.contenido());
        m.setAdjuntoUrl(cmd.adjuntoUrl());
        m.setEstado(estado);
        m.setAutorId(cmd.autorId());
        MensajeForo saved = port.saveMensaje(m);
        return Map.of("id", saved.getId(), "estado", estado, "message", "Mensaje publicado");
    }

    @Override
    public Map<String, Object> responder(UUID foroId, UUID mensajeId, String contenido, String adjuntoUrl, UUID autorId) {
        port.findMensajeById(mensajeId)
                .filter(msg -> msg.getForoId().equals(foroId) && msg.getIsActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
        RespuestaForo rf = new RespuestaForo();
        rf.setMensajeId(mensajeId);
        rf.setContenido(contenido);
        rf.setAdjuntoUrl(adjuntoUrl);
        rf.setAutorId(autorId);
        RespuestaForo saved = port.saveRespuesta(rf);
        return Map.of("id", saved.getId(), "message", "Respuesta publicada");
    }

    @Override
    public Map<String, Object> moderar(UUID mensajeId, String estado, Integer nivelAcceso) {
        if (nivelAcceso != null && nivelAcceso > 3) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol de Coordinador o superior para moderar contenido.");
        }
        if (!List.of("PUBLICADO", "RECHAZADO", "PENDIENTE").contains(estado.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido.");
        }
        MensajeForo m = port.findMensajeById(mensajeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado."));
        m.setEstado(estado.toUpperCase());
        m.setIsActive(!"RECHAZADO".equalsIgnoreCase(estado));
        port.saveMensaje(m);
        return Map.of("message", "Mensaje moderado correctamente. Estado: " + estado);
    }

    @Override
    public Map<String, Object> publicar(PublicarAnuncioUseCase.Command cmd) {
        Anuncio a = new Anuncio();
        a.setTitulo(cmd.titulo());
        a.setContenido(cmd.contenido());
        a.setPlantelId(cmd.plantelId());
        a.setNivelEducativo(cmd.nivelEducativo());
        if (cmd.fechaInicio() != null) a.setFechaInicio(parseFecha(cmd.fechaInicio()));
        if (cmd.fechaFin() != null) a.setFechaFin(parseFecha(cmd.fechaFin()));
        a.setEsUrgente(Boolean.TRUE.equals(cmd.esUrgente()));
        Anuncio saved = port.saveAnuncio(a);
        return Map.of("id", saved.getId(), "message", "Anuncio publicado");
    }

    /** ISO-8601 estricto (YYYY-MM-DD); 400 claro en vez de 500 ante un formato inválido. */
    private LocalDate parseFecha(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha con formato inválido (esperado YYYY-MM-DD): " + raw);
        }
    }
}
