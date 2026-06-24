package mx.ades.modules.encuestas.application.service;

import mx.ades.modules.encuestas.domain.port.in.ResponderEncuestaUseCase;
import mx.ades.modules.encuestas.domain.port.out.EncuestaRespuestaRepositoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: respuesta a encuestas institucionales con detección de alertas de acoso.
 * Implementa {@link ResponderEncuestaUseCase} coordinando el dominio de encuestas
 * con el puerto de repositorio, con soporte para encuestas anónimas y detección
 * automática de palabras clave relacionadas con bullying o situaciones de riesgo.
 *
 * @author ADES
 * @since 2026
 */
public class EncuestaApplicationService implements ResponderEncuestaUseCase {

    private static final List<String> PALABRAS_ACOSO = List.of(
            "acoso", "bullying", "cyberbullying", "golpe", "insult", "burl", "amenaz",
            "maltrat", "pegan", "miedo", "lloro", "suicid", "cortar", "dañar", "morir", "agred");

    private final EncuestaRespuestaRepositoryPort repo;

    public EncuestaApplicationService(EncuestaRespuestaRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Result ejecutar(Command command) {
        EncuestaRespuestaRepositoryPort.EncuestaEstado estado = repo.findEstado(command.encuestaId());

        if (!estado.activa()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La encuesta no está activa");
        }

        UUID usuarioEfectivo = estado.anonima() ? null : command.usuarioId();
        int guardadas = 0;

        for (RespuestaItem item : command.respuestas()) {
            if (repo.existeRespuesta(item.preguntaId(), command.sesionId())) continue;

            repo.guardarRespuesta(new EncuestaRespuestaRepositoryPort.RespuestaData(
                    command.encuestaId(), item.preguntaId(), usuarioEfectivo,
                    command.sesionId(), item.textoRespuesta(),
                    item.valorNumerico(), item.opcionSeleccionada()));
            guardadas++;

            if (esFlagged(item.textoRespuesta())) {
                UUID estudianteId = usuarioEfectivo != null ? repo.findEstudianteIdPorUsuario(usuarioEfectivo) : null;
                repo.crearAlertaBullying(estudianteId, estado.plantelId(),
                        item.textoRespuesta(), estado.titulo(), command.sesionId());
            }
        }

        return new Result(command.sesionId(), guardadas);
    }

    private boolean esFlagged(String texto) {
        if (texto == null || texto.isBlank()) return false;
        String lower = texto.toLowerCase();
        return PALABRAS_ACOSO.stream().anyMatch(lower::contains);
    }
}
