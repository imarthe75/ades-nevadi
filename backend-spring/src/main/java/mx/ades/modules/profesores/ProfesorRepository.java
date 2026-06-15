package mx.ades.modules.profesores;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProfesorRepository extends JpaRepository<Profesor, UUID> {
    List<Profesor> findByPlantelId(UUID plantelId);
}
