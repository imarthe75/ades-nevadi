package mx.ades.modules.encuestas;

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
public interface EncuestaRepository extends JpaRepository<Encuesta, UUID> {

    @EntityGraph(attributePaths = {"preguntas", "preguntas.opciones", "grupoDestino"})
    Optional<Encuesta> findById(UUID id);

    @EntityGraph(attributePaths = {"preguntas", "grupoDestino"})
    @Query("SELECT e FROM Encuesta e WHERE e.isActive = true ORDER BY e.fechaCreacion DESC")
    List<Encuesta> findAllActiveOrderByFecha();

    @EntityGraph(attributePaths = {"preguntas", "grupoDestino"})
    @Query("SELECT e FROM Encuesta e WHERE e.grupoDestino.id = :grupoId AND e.isActive = true")
    List<Encuesta> findByGrupoIdActive(@Param("grupoId") UUID grupoId);
}
