package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Nee no mapea relaciones JPA — alumno_id es FK plana, no aplica
 * @EntityGraph.
 */
@Repository
public interface NeeRepository extends JpaRepository<Nee, UUID> {

    List<Nee> findByAlumnoId(UUID alumnoId);

    List<Nee> findByActivaTrue();
}
