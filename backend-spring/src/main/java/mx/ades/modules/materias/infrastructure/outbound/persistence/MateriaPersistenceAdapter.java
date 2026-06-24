package mx.ades.modules.materias.infrastructure.outbound.persistence;

import mx.ades.modules.materias.Materia;
import mx.ades.modules.materias.MateriaRepository;
import mx.ades.modules.materias.domain.port.out.MateriaRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA que implementa {@link MateriaRepositoryPort}.
 * Delega en el repositorio Spring Data de Materia.
 *
 * @author ADES
 * @since 2026
 */
@Component
public class MateriaPersistenceAdapter implements MateriaRepositoryPort {

    private final MateriaRepository repository;

    public MateriaPersistenceAdapter(MateriaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Materia save(Materia materia) {
        return repository.save(materia);
    }

    @Override
    public Optional<Materia> findById(UUID id) {
        return repository.findById(id);
    }
}
