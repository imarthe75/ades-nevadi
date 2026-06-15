package mx.ades.modules.planteles;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlantelRepository extends JpaRepository<Plantel, UUID> {
}
