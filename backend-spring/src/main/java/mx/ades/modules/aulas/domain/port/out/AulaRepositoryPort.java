package mx.ades.modules.aulas.domain.port.out;

import mx.ades.modules.aulas.Aula;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AulaRepositoryPort {

    Aula save(Aula aula);

    Optional<Aula> findById(UUID id);

    List<Aula> findAll();

    List<Aula> findByPlantelId(UUID plantelId);
}
