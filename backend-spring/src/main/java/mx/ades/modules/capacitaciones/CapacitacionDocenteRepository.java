package mx.ades.modules.capacitaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CapacitacionDocenteRepository extends JpaRepository<CapacitacionDocente, UUID> {
}
