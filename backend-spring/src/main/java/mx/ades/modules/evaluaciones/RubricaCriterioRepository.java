package mx.ades.modules.evaluaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RubricaCriterioRepository extends JpaRepository<RubricaCriterio, UUID> {
    List<RubricaCriterio> findByRubricaIdAndIsActiveTrueOrderByOrdenAsc(UUID rubricaId);
}
