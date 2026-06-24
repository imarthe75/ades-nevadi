package mx.ades.modules.foros.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para publicar un mensaje o respuesta dentro de un foro.
 *
 * @author ADES
 * @since 2026
 */
public interface PublicarMensajeUseCase {

    record Command(
            UUID foroId,
            String asunto,
            String contenido,
            String adjuntoUrl,
            UUID autorId
    ) {}

    Map<String, Object> publicar(Command cmd);

    Map<String, Object> responder(UUID foroId, UUID mensajeId, String contenido, String adjuntoUrl, UUID autorId);
}
