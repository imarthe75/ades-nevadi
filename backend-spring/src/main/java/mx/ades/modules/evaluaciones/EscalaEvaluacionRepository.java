package mx.ades.modules.evaluaciones;

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
public interface EscalaEvaluacionRepository extends JpaRepository<EscalaEvaluacion, UUID> {

    @EntityGraph(attributePaths = {"items"})
    List<EscalaEvaluacion> findByNivelEducativoAndIsActiveTrueOrderByNombre(String nivelEducativo);

    @EntityGraph(attributePaths = {"items"})
    List<EscalaEvaluacion> findByIsActiveTrueOrderByNivelEducativoAscNombreAsc();

    @EntityGraph(attributePaths = {"items"})
    Optional<EscalaEvaluacion> findById(UUID id);
}
