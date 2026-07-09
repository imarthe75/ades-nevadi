package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
@Repository
public interface EvaluacionRepository extends JpaRepository<Evaluacion, UUID> {

    @EntityGraph(attributePaths = {"grupo", "grupo.grado", "grupo.grado.nivel", "rubrica"})
    List<Evaluacion> findByGrupoId(UUID grupoId);

    @EntityGraph(attributePaths = {"grupo", "grupo.grado", "rubrica"})
    Optional<Evaluacion> findById(UUID id);

    @EntityGraph(attributePaths = {"grupo", "rubrica"})
    @Query("SELECT e FROM Evaluacion e WHERE e.rubrica.id = :rubricaId")
    List<Evaluacion> findByRubricaId(@Param("rubricaId") UUID rubricaId);
}
