package mx.ades.modules.materias;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MateriaRepository extends JpaRepository<Materia, UUID> {
    List<Materia> findByNivelEducativoId(UUID nivelEducativoId);
}
