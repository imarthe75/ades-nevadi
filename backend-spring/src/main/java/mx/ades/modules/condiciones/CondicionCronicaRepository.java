package mx.ades.modules.condiciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CondicionCronicaRepository extends JpaRepository<CondicionCronica, UUID> {
}
