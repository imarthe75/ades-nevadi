package mx.ades.modules.comunicados;

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
public interface ComunicadoRepository extends JpaRepository<Comunicado, UUID> {

    @EntityGraph(attributePaths = {"autor", "plantel", "destinatarios"})
    List<Comunicado> findByIsActiveTrue();

    @EntityGraph(attributePaths = {"autor", "plantel", "destinatarios"})
    Optional<Comunicado> findById(UUID id);

    @EntityGraph(attributePaths = {"autor", "plantel"})
    @Query("SELECT c FROM Comunicado c WHERE c.plantel.id = :plantelId AND c.isActive = true ORDER BY c.fechaCreacion DESC")
    List<Comunicado> findByPlantelIdActive(@Param("plantelId") UUID plantelId);
}
