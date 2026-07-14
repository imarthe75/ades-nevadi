package mx.ades.modules.encuestas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Encuesta no mapea relaciones JPA — solo FKs planas (plantel_id,
 * grupo_id, etc.), no aplica @EntityGraph.
 */
@Repository
public interface EncuestaRepository extends JpaRepository<Encuesta, UUID> {

    @Query("SELECT e FROM Encuesta e WHERE e.isActive = true ORDER BY e.fechaCreacion DESC")
    List<Encuesta> findAllActiveOrderByFecha();

    @Query("SELECT e FROM Encuesta e WHERE e.grupoId = :grupoId AND e.isActive = true")
    List<Encuesta> findByGrupoIdActive(@Param("grupoId") UUID grupoId);
}
