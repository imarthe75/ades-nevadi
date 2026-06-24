package mx.ades.modules.comunicados.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para registrar el acuse de recibo de un comunicado en el módulo comunicados.
 * <p>Permite que un usuario confirme la lectura de un comunicado; idempotente por par (comunicadoId, usuarioId).</p>
 *
 * @author ADES
 * @since 2026
 */
public interface AcusarComunicadoUseCase {
    void acusar(UUID comunicadoId, UUID usuarioId);
}
