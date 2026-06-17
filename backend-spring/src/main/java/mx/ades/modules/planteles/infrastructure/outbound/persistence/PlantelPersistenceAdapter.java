package mx.ades.modules.planteles.infrastructure.outbound.persistence;

import mx.ades.modules.planteles.Plantel;
import mx.ades.modules.planteles.PlantelRepository;
import mx.ades.modules.planteles.domain.port.out.PlantelRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlantelPersistenceAdapter implements PlantelRepositoryPort {

    private final PlantelRepository repository;

    public PlantelPersistenceAdapter(PlantelRepository repository) {
        this.repository = repository;
    }

    @Override
    public Plantel save(Plantel plantel) {
        return repository.save(plantel);
    }

    @Override
    public Optional<Plantel> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Plantel> findAll() {
        return repository.findAll();
    }
}
