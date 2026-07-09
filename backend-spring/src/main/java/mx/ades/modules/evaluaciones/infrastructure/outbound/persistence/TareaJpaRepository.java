package mx.ades.modules.evaluaciones.infrastructure.outbound.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para {@link TareaEntity} ({@code ades_tareas}).
 * PUNTO 1: @EntityGraph implementado para prevenir N+1 queries
 *
 * @author ADES
 * @since 2026
 */
public interface TareaJpaRepository extends JpaRepository<TareaEntity, UUID> {

    @EntityGraph(attributePaths = {"planeacion", "planeacion.grupo", "rubrica"})
    Optional<TareaEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"planeacion", "planeacion.grupo", "rubrica"})
    @Query("SELECT t FROM TareaEntity t WHERE t.planeacion.id = :planeacionId")
    List<TareaEntity> findByPlaneacionId(@Param("planeacionId") UUID planeacionId);

    @EntityGraph(attributePaths = {"planeacion", "rubrica"})
    List<TareaEntity> findAll();
}
