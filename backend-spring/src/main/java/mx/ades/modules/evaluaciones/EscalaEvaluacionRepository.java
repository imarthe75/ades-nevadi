package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * EscalaEvaluacion no mapea relaciones JPA — valores_json es una columna
 * jsonb plana, no aplica @EntityGraph.
 */
@Repository
public interface EscalaEvaluacionRepository extends JpaRepository<EscalaEvaluacion, UUID> {

    List<EscalaEvaluacion> findByNivelEducativoAndIsActiveTrueOrderByNombre(String nivelEducativo);

    List<EscalaEvaluacion> findByIsActiveTrueOrderByNivelEducativoAscNombreAsc();
}
