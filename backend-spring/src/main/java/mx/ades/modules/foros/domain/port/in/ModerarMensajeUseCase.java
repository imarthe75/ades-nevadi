package mx.ades.modules.foros.domain.port.in;

import java.util.Map;
import java.util.UUID;

public interface ModerarMensajeUseCase {

    Map<String, Object> moderar(UUID mensajeId, String estado, Integer nivelAcceso);
}
