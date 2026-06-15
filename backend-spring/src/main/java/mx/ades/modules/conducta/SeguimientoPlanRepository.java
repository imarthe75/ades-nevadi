package mx.ades.modules.conducta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SeguimientoPlanRepository extends JpaRepository<SeguimientoPlan, UUID> {
}
