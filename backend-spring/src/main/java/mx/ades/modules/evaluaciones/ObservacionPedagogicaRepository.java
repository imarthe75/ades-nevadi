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
public interface ObservacionPedagogicaRepository extends JpaRepository<ObservacionPedagogica, UUID> {

    @EntityGraph(attributePaths = {"alumno", "alumno.persona", "docente", "docente.persona"})
    List<ObservacionPedagogica> findByAlumnoIdOrderByFechaCreacionDesc(UUID alumnoId);

    @EntityGraph(attributePaths = {"alumno", "alumno.persona", "docente", "docente.persona"})
    List<ObservacionPedagogica> findByAlumnoIdAndTipoOrderByFechaCreacionDesc(UUID alumnoId, String tipo);

    @EntityGraph(attributePaths = {"alumno", "alumno.persona", "docente"})
    Optional<ObservacionPedagogica> findById(UUID id);
}
