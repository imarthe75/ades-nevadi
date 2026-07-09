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
public interface RubricaRepository extends JpaRepository<Rubrica, UUID> {

    @EntityGraph(attributePaths = {"materia", "criterios", "criterios.escala"})
    List<Rubrica> findByMateriaId(UUID materiaId);

    @EntityGraph(attributePaths = {"materia", "criterios", "criterios.escala"})
    Optional<Rubrica> findById(UUID id);
}
