package mx.ades.modules.planteles.domain.port.out;

import mx.ades.modules.planteles.Plantel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlantelRepositoryPort {

    Plantel save(Plantel plantel);

    Optional<Plantel> findById(UUID id);

    List<Plantel> findAll();
}
