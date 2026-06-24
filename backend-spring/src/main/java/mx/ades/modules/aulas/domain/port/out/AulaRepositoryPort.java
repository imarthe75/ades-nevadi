package mx.ades.modules.aulas.domain.port.out;

import mx.ades.modules.aulas.Aula;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: define el contrato de persistencia para la entidad {@code Aula}.
 *
 * @author ADES
 * @since 2026
 */
public interface AulaRepositoryPort {

    Aula save(Aula aula);

    Optional<Aula> findById(UUID id);

    List<Aula> findAll();

    List<Aula> findByPlantelId(UUID plantelId);
}
