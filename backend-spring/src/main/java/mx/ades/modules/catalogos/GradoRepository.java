package mx.ades.modules.catalogos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradoRepository extends JpaRepository<Grado, UUID> {
    List<Grado> findByNivelEducativoId(UUID nivelEducativoId);
    List<Grado> findByPlantelId(UUID plantelId);
    List<Grado> findByNivelEducativoIdAndPlantelId(UUID nivelEducativoId, UUID plantelId);
}
