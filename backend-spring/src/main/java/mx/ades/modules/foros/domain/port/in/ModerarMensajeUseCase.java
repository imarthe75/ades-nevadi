package mx.ades.modules.foros.domain.port.in;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada: contrato para moderar (aprobar/rechazar) un mensaje de foro.
 *
 * @author ADES
 * @since 2026
 */
public interface ModerarMensajeUseCase {

    Map<String, Object> moderar(UUID mensajeId, String estado, Integer nivelAcceso);
}
