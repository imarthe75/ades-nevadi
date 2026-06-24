package mx.ades.modules.comunicados.domain.port.out;

import mx.ades.modules.comunicados.AcuseComunicado;
import mx.ades.modules.comunicados.Comunicado;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para comunicados y acuses de recibo.
 * <p>También expone métodos para resolver la lista de destinatarios según el alcance
 * del comunicado (grupo, nivel, plantel o global).</p>
 *
 * @author ADES
 * @since 2026
 */
public interface ComunicadoRepositoryPort {
    Comunicado save(Comunicado comunicado);
    Optional<Comunicado> findById(UUID id);
    Optional<AcuseComunicado> findAcuse(UUID comunicadoId, UUID usuarioId);
    AcuseComunicado saveAcuse(AcuseComunicado acuse);
    List<UUID> getRecipientsForGrupo(UUID grupoId);
    List<UUID> getRecipientsForNivel(UUID nivelId);
    List<UUID> getRecipientsForPlantel(UUID plantelId);
    List<UUID> getAllRecipients();
}
