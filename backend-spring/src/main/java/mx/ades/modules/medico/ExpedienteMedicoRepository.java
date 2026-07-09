package mx.ades.modules.medico;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
@Repository
public interface ExpedienteMedicoRepository extends JpaRepository<ExpedienteMedico, UUID> {

    @EntityGraph(attributePaths = {"estudiante", "estudiante.persona", "condiciones", "medicinas", "alergias"})
    Optional<ExpedienteMedico> findByEstudianteId(UUID estudianteId);

    @EntityGraph(attributePaths = {"estudiante", "estudiante.persona", "condiciones", "medicinas"})
    Optional<ExpedienteMedico> findById(UUID id);
}
