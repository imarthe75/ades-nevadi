package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Rubrica no mapea relaciones JPA — materia_id es FK plana, no aplica
 * @EntityGraph.
 */
@Repository
public interface RubricaRepository extends JpaRepository<Rubrica, UUID> {

    List<Rubrica> findByMateriaId(UUID materiaId);
}
