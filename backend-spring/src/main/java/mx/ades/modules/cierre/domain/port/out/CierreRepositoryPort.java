package mx.ades.modules.cierre.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de persistencia para el proceso de cierre de ciclo
 * sobre la tabla {@code ades_ciclos_escolares}.
 *
 * @author ADES
 * @since 2026
 */
public interface CierreRepositoryPort {

    Optional<String> fetchEstado(UUID cicloId);

    void marcarCerrado(UUID cicloId);
}
