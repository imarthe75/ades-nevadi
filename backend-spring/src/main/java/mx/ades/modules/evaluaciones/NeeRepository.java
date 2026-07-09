package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
@Repository
public interface NeeRepository extends JpaRepository<Nee, UUID> {

    @EntityGraph(attributePaths = {"estudiante", "estudiante.persona", "planEspecializado"})
    Optional<Nee> findById(UUID id);

    @EntityGraph(attributePaths = {"estudiante", "estudiante.persona"})
    @Query("SELECT n FROM Nee n WHERE n.estudiante.id = :estudianteId")
    List<Nee> findByEstudianteId(UUID estudianteId);

    @EntityGraph(attributePaths = {"estudiante", "planEspecializado"})
    @Query("SELECT n FROM Nee n WHERE n.isActive = true")
    List<Nee> findAllActive();
}
