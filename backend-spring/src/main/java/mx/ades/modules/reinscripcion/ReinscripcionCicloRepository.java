package mx.ades.modules.reinscripcion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReinscripcionCicloRepository extends JpaRepository<ReinscripcionCiclo, UUID> {
    Optional<ReinscripcionCiclo> findByEstudianteIdAndCicloDestinoId(UUID estudianteId, UUID cicloDestinoId);
    List<ReinscripcionCiclo> findByCicloDestinoId(UUID cicloDestinoId);
}
