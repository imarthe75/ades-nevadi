package mx.ades.modules.foros.domain.port.out;

import mx.ades.modules.foros.Anuncio;
import mx.ades.modules.foros.Foro;
import mx.ades.modules.foros.MensajeForo;
import mx.ades.modules.foros.RespuestaForo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para foros, mensajes, respuestas y anuncios.
 *
 * @author ADES
 * @since 2026
 */
public interface ForoRepositoryPort {

    // Foros
    Foro saveForo(Foro foro);
    Optional<Foro> findForoById(UUID id);
    List<Foro> findAllForos();

    // Mensajes
    MensajeForo saveMensaje(MensajeForo mensaje);
    Optional<MensajeForo> findMensajeById(UUID id);
    List<MensajeForo> findMensajesByForo(UUID foroId);

    // Respuestas
    RespuestaForo saveRespuesta(RespuestaForo respuesta);

    // Anuncios
    Anuncio saveAnuncio(Anuncio anuncio);
    List<Anuncio> findAllAnuncios();
}
