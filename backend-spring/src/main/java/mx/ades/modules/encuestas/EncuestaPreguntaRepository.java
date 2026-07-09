package mx.ades.modules.encuestas;

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
public interface EncuestaPreguntaRepository extends JpaRepository<EncuestaPregunta, UUID> {

    @EntityGraph(attributePaths = {"encuesta", "opciones"})
    List<EncuestaPregunta> findByEncuestaIdAndIsActiveTrueOrderByOrden(UUID encuestaId);

    @EntityGraph(attributePaths = {"encuesta", "opciones"})
    Optional<EncuestaPregunta> findById(UUID id);
}
