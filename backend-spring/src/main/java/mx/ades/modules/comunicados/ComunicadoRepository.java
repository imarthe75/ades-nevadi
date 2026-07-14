package mx.ades.modules.comunicados;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Comunicado no mapea relaciones JPA — solo FKs planas (plantel_id,
 * creado_por_id, grupo_id), no aplica @EntityGraph.
 */
@Repository
public interface ComunicadoRepository extends JpaRepository<Comunicado, UUID> {

    List<Comunicado> findByIsActiveTrue();

    @Query("SELECT c FROM Comunicado c WHERE c.plantelId = :plantelId AND c.isActive = true ORDER BY c.fechaCreacion DESC")
    List<Comunicado> findByPlantelIdActive(@Param("plantelId") UUID plantelId);
}
