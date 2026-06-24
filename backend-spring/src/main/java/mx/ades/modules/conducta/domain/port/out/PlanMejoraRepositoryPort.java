package mx.ades.modules.conducta.domain.port.out;

import mx.ades.modules.conducta.domain.port.in.CrearPlanMejoraUseCase;

import java.util.UUID;

/**
 * Puerto de salida: contrato de persistencia para planes de mejora conductual.
 * <p>Almacena planes en {@code ades_planes_mejora} con compromisos en formato JSONB.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface PlanMejoraRepositoryPort {
    boolean existeActivo(UUID reporteId);
    UUID guardar(CrearPlanMejoraUseCase.Command command);
}
