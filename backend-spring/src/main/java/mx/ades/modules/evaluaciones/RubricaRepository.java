package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RubricaRepository extends JpaRepository<Rubrica, UUID> {
    List<Rubrica> findByMateriaId(UUID materiaId);
}
