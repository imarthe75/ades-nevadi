package mx.ades.modules.catalogos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NivelEducativoRepository extends JpaRepository<NivelEducativo, UUID> {
}
