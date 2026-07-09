package mx.ades.modules.alumnos;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstudianteRepository extends JpaRepository<Estudiante, UUID> {

    @EntityGraph(attributePaths = {"persona", "plantel", "estatus"})
    List<Estudiante> findByPlantelId(UUID plantelId);

    @EntityGraph(attributePaths = {"persona", "plantel", "estatus"})
    Optional<Estudiante> findById(UUID id);

    @EntityGraph(attributePaths = {"persona", "plantel", "estatus"})
    List<Estudiante> findByIsActiveTrue();
}
