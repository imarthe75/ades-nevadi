package mx.ades.modules.sistema;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

/**
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 */
public interface CatalogoRepository extends JpaRepository<Catalogo, UUID> {

    @EntityGraph(attributePaths = {"items"})
    List<Catalogo> findByIsActiveTrueOrderByCodigo();

    @EntityGraph(attributePaths = {"items"})
    Optional<Catalogo> findByIdAndIsActiveTrue(UUID id);

    @EntityGraph(attributePaths = {"items"})
    Optional<Catalogo> findById(UUID id);
}
