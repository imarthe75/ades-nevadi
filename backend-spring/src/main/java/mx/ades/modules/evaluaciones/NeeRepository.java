package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NeeRepository extends JpaRepository<Nee, UUID> {
}
