package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AsignacionAulaRepository extends JpaRepository<AsignacionAula, UUID> {
}
