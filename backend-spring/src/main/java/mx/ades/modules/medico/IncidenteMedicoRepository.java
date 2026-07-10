package mx.ades.modules.medico;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
@Repository
public interface IncidenteMedicoRepository extends JpaRepository<IncidenteMedico, UUID> {

    @EntityGraph(attributePaths = {"estudiante", "estudiante.persona", "tipo"})
    List<IncidenteMedico> findByEstudianteIdAndIsActiveTrueOrderByFechaIncidenteDesc(UUID estudianteId, Pageable pageable);

    @EntityGraph(attributePaths = {"estudiante", "estudiante.persona", "tipo"})
    Optional<IncidenteMedico> findById(UUID id);
}
