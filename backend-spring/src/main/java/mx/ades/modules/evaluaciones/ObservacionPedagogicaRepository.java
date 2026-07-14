package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * ObservacionPedagogica no mapea relaciones JPA — alumno_id y autor_id son
 * FKs planas, no aplica @EntityGraph.
 */
@Repository
public interface ObservacionPedagogicaRepository extends JpaRepository<ObservacionPedagogica, UUID> {

    List<ObservacionPedagogica> findByAlumnoIdOrderByFechaCreacionDesc(UUID alumnoId);

    List<ObservacionPedagogica> findByAlumnoIdAndTipoOrderByFechaCreacionDesc(UUID alumnoId, String tipo);
}
