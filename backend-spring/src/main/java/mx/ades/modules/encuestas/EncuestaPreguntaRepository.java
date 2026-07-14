package mx.ades.modules.encuestas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * EncuestaPregunta no mapea relaciones JPA — encuesta_id es FK plana y
 * opciones es una columna jsonb, no aplica @EntityGraph.
 */
@Repository
public interface EncuestaPreguntaRepository extends JpaRepository<EncuestaPregunta, UUID> {

    List<EncuestaPregunta> findByEncuestaIdAndIsActiveTrueOrderByOrden(UUID encuestaId);
}
