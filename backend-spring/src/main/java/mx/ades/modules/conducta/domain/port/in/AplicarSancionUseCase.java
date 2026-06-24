package mx.ades.modules.conducta.domain.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: contrato para aplicar una sanción disciplinaria en el módulo conducta.
 * <p>Cada reporte de conducta solo puede tener una sanción activa; el caso de uso verifica
 * la unicidad antes de persistir.</p>
 *
 * @author ADES
 * @since 2026
 */
public interface AplicarSancionUseCase {
    UUID ejecutar(AplicarSancionCommand command);
}
