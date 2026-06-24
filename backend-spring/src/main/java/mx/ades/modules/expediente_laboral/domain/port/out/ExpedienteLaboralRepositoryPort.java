package mx.ades.modules.expediente_laboral.domain.port.out;

import mx.ades.modules.expediente_laboral.domain.port.in.ActualizarExpedienteLaboralUseCase;
import mx.ades.modules.expediente_laboral.domain.port.in.CrearExpedienteLaboralUseCase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para expedientes laborales del personal.
 *
 * @author ADES
 * @since 2026
 */
public interface ExpedienteLaboralRepositoryPort {
    List<Map<String, Object>> list(UUID personaId, String tipoContrato, String q);
    UUID insert(CrearExpedienteLaboralUseCase.Command cmd);
    Optional<Map<String, Object>> findById(UUID id);
    Map<String, Object> patch(UUID id, ActualizarExpedienteLaboralUseCase.Patch p, String usuarioId);
    void agregarDocumento(UUID id, String tipoDocumento, String url, String usuarioId);
    Map<String, Object> fetchById(UUID id);
    void softDelete(UUID id, String usuarioId);
}
