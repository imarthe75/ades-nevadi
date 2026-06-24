package mx.ades.modules.aulas.infrastructure.outbound.persistence;

import mx.ades.modules.aulas.Aula;
import mx.ades.modules.aulas.AulaRepository;
import mx.ades.modules.aulas.domain.port.out.AulaRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia que implementa {@link AulaRepositoryPort} accediendo
 * a la tabla {@code ades_aulas} vía JPA ({@code AulaRepository}).
 *
 * @author ADES
 * @since 2026
 */
@Component
public class AulaPersistenceAdapter implements AulaRepositoryPort {

    private final AulaRepository repository;

    public AulaPersistenceAdapter(AulaRepository repository) {
        this.repository = repository;
    }

    @Override public Aula save(Aula aula)                       { return repository.save(aula); }
    @Override public Optional<Aula> findById(UUID id)           { return repository.findById(id); }
    @Override public List<Aula> findAll()                       { return repository.findAll(); }
    @Override public List<Aula> findByPlantelId(UUID plantelId) { return repository.findByPlantelId(plantelId); }
}
