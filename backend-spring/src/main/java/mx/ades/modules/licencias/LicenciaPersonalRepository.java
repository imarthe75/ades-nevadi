package mx.ades.modules.licencias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LicenciaPersonalRepository extends JpaRepository<LicenciaPersonal, UUID> {
}
