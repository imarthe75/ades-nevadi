package mx.ades.modules.alumnos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Estudiante no mapea relaciones JPA — persona_id, plantel_id y estatus_id
 * son FKs planas, no aplica @EntityGraph.
 */
@Repository
public interface EstudianteRepository extends JpaRepository<Estudiante, UUID> {

    List<Estudiante> findByPlantelId(UUID plantelId);

    List<Estudiante> findByIsActiveTrue();
}
